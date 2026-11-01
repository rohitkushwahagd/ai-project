package dev.rohit.rag;

import java.util.*;
import java.time.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.ai.model.chat=none","spring.ai.model.embedding=none","app.outbox.delay=100"})
class InfrastructureIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @Container static KafkaContainer kafka=new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    @Container static org.testcontainers.containers.GenericContainer<?> redisContainer=new org.testcontainers.containers.GenericContainer<>(
        DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
        r.add("spring.data.redis.host",redisContainer::getHost);
        r.add("spring.data.redis.port",()->redisContainer.getMappedPort(6379));
    }
    @MockitoBean EmbeddingModel model;
    @MockitoBean ChatModel chat;
    @Autowired StringRedisTemplate redis;
    @Autowired LocalCache cache;
    @Autowired SourceRepository repository;
    @Autowired JdbcTemplate db;
    @Autowired IngestionPipeline pipeline;
    @LocalServerPort int port;
    @BeforeEach void configureModel() {
        float[] vector=new float[768]; vector[0]=1;
        when(model.embed(anyString())).thenReturn(vector);
    }
    @Test void realKafkaIndexesOnceAndScopesSourcesAndVectors() throws Exception {
        var input=new ApiTypes.Ingest("runbooks","test-"+UUID.randomUUID(),"Orion acknowledge in 17 minutes.");
        UUID id=repository.enqueue("alpha",input);
        assertThat(repository.enqueue("alpha",input)).isEqualTo(id);
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(!repository.get("alpha",id).status().equals("READY") && System.nanoTime()<deadline) Thread.sleep(100);
        assertThat(repository.get("alpha",id).status()).isEqualTo("READY");
        pipeline.index(id.toString());
        assertThat(db.queryForObject("SELECT count(*) FROM chunks WHERE source_id=?",Integer.class,id)).isEqualTo(1);
        float[] vector=new float[768]; vector[0]=1;
        assertThat(repository.search("alpha",vector,5)).isNotEmpty();
        assertThat(repository.search("beta",vector,5)).isEmpty();
        assertThatThrownBy(()->repository.get("beta",id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void realRedisRoundTripRetainsTenantKeysAndExpiry() {
        String key="alpha:"+UUID.randomUUID();
        cache.put("integration",key,Map.of("answer","Alpha only"),Duration.ofSeconds(30));
        assertThat(cache.get("integration",key,Map.class)).containsEntry("answer","Alpha only");
        assertThat(cache.get("integration","beta:"+key,Map.class)).isNull();
        assertThat(redis.getExpire("integration:"+key)).isBetween(1L,30L);
    }
    @Test void sdkMcpContractListsToolsAndRejectsCrossTenantReads() {
        UUID id=repository.enqueue("alpha",new ApiTypes.Ingest("tickets","MCP test","Synthetic incident"));
        var transport=HttpClientStreamableHttpTransport.builder("http://localhost:"+port).endpoint("/mcp")
            .openConnectionOnStartup(false).customizeRequest(r->r.header("Authorization","Bearer "+token("beta"))).build();
        var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
        try {
            client.initialize();
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("ticket_lookup","runbook_fetch","escalation");
            var result=client.callTool(new McpSchema.CallToolRequest("ticket_lookup",Map.of("sourceId",id.toString())));
            assertThat(result.isError()).isTrue();
        } finally { client.close(); }
    }
    @Test void sdkMcpContractAllowsSameTenantReadAndScopesEscalation() {
        UUID id=repository.enqueue("alpha",new ApiTypes.Ingest("tickets","MCP positive","Synthetic incident"));
        var readClient=mcpClient(token("alpha","rag.read"));
        try {
            readClient.initialize();
            var lookup=readClient.callTool(new McpSchema.CallToolRequest("ticket_lookup",Map.of("sourceId",id.toString())));
            assertThat(lookup.isError()).isFalse();
            assertThat(lookup.content().toString()).contains("Synthetic incident");
            var denied=readClient.callTool(new McpSchema.CallToolRequest("escalation",Map.of("sourceId",id.toString(),"reason","investigate")));
            assertThat(denied.isError()).isTrue();
        } finally { readClient.close(); }
        var actionClient=mcpClient(token("alpha","rag.read rag.escalate"));
        try {
            actionClient.initialize();
            var action=actionClient.callTool(new McpSchema.CallToolRequest("escalation",Map.of("sourceId",id.toString(),"reason","investigate")));
            assertThat(action.isError()).isFalse();
            assertThat(action.content().toString()).contains("RECORDED_SYNTHETIC");
        } finally { actionClient.close(); }
    }
    private McpSyncClient mcpClient(String bearer) {
        var transport=HttpClientStreamableHttpTransport.builder("http://localhost:"+port).endpoint("/mcp")
            .openConnectionOnStartup(false).customizeRequest(r->r.header("Authorization","Bearer "+bearer)).build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
    }
    private String token(String tenant) {
        return token(tenant,"rag.read");
    }
    private String token(String tenant,String scope) {
        var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32],"HmacSHA256")));
        var claims=JwtClaimsSet.builder().issuer("project1-local").subject("test").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300)).audience(List.of("project1-api")).claim("tenant_id",tenant).claim("scope",scope).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
}
