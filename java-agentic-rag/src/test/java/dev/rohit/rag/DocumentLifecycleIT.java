package dev.rohit.rag;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.ai.model.chat=none","spring.ai.model.embedding=none",
        "spring.kafka.listener.auto-startup=false","spring.kafka.admin.auto-create=false","app.outbox.delay=3600000"})
class DocumentLifecycleIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
    }
    @MockitoBean EmbeddingModel model;
    @MockitoBean ChatModel chat;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean KafkaTemplate<String,String> kafka;
    @Autowired SourceRepository repository;
    @Autowired IngestionPipeline pipeline;
    @Autowired JdbcTemplate db;
    final float[] vector=new float[768];
    @BeforeEach void model() { vector[0]=1; when(model.embed(anyString())).thenReturn(vector); }

    @Test void replacementActivatesAtomicallyAndDeletionCannotBeUndoneByDelayedMessages() {
        String tenant="lifecycle-"+UUID.randomUUID();
        UUID old=repository.ingest(tenant,input("old"),"create");
        pipeline.index(old.toString());
        long before=repository.revision(tenant);
        UUID next=repository.replace(tenant,old,input("new"),"replace");
        assertThat(repository.replace(tenant,old,input("new"),"replace")).isEqualTo(next);
        assertThat(repository.search(tenant,vector,5)).extracting(ApiTypes.Evidence::sourceId).containsExactly(old);
        assertThat(repository.revision(tenant)).isEqualTo(before);
        pipeline.index(next.toString());
        pipeline.index(old.toString());
        assertThat(repository.search(tenant,vector,5)).extracting(ApiTypes.Evidence::sourceId).containsExactly(next);
        assertThat(repository.revision(tenant)).isEqualTo(before+1);
        repository.delete(tenant,old);
        repository.delete(tenant,next);
        pipeline.index(next.toString());
        pipeline.deadLetter(next.toString());
        assertThat(repository.revision(tenant)).isEqualTo(before+2);
        assertThat(repository.search(tenant,vector,5)).isEmpty();
        assertThatThrownBy(()->repository.get(tenant,next)).hasMessageContaining("404");
        assertThat(db.queryForObject("SELECT count(*) FROM chunks WHERE tenant=?",Integer.class,tenant)).isZero();
    }
    @Test void idempotencyKeysAreTenantScopedAndPayloadConflictsAreRejected() {
        String tenant="request-"+UUID.randomUUID();
        UUID id=repository.ingest(tenant,input("a"),"key");
        assertThat(repository.ingest(tenant,input("a"),"key")).isEqualTo(id);
        assertThatThrownBy(()->repository.ingest(tenant,input("b"),"key")).hasMessageContaining("409");
        assertThat(repository.ingest("other-"+tenant,input("b"),"key")).isNotEqualTo(id);
        assertThatThrownBy(()->repository.delete("other-"+tenant,id)).hasMessageContaining("404");
        assertThatThrownBy(()->repository.replace("other-"+tenant,id,input("x"),"replace")).hasMessageContaining("404");
    }
    @Test void simultaneousRetriesProduceOneSourceAndOneOutboxEntry() throws Exception {
        String tenant="concurrent-"+UUID.randomUUID();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1);
            var a=workers.submit(()->{start.await(); return repository.ingest(tenant,input("same"),"same-key");});
            var b=workers.submit(()->{start.await(); return repository.ingest(tenant,input("same"),"same-key");});
            start.countDown();
            UUID id=a.get(15,TimeUnit.SECONDS);
            assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(db.queryForObject("SELECT count(*) FROM ingestion_outbox WHERE source_id=?",Integer.class,id)).isEqualTo(1);
        }
    }
    @Test void failedReplacementKeepsOldEvidenceAndAllowsANewAttempt() {
        String tenant="failure-"+UUID.randomUUID();
        UUID original=repository.enqueue(tenant,input("original")); pipeline.index(original.toString());
        UUID failed=repository.replace(tenant,original,input("failed"),"first"); pipeline.deadLetter(failed.toString());
        pipeline.index(failed.toString());
        assertThat(repository.search(tenant,vector,5)).extracting(ApiTypes.Evidence::sourceId).containsExactly(original);
        UUID retry=repository.replace(tenant,original,input("retry"),"second"); pipeline.index(retry.toString());
        assertThat(repository.search(tenant,vector,5)).extracting(ApiTypes.Evidence::sourceId).containsExactly(retry);
    }
    private ApiTypes.Ingest input(String text) { return new ApiTypes.Ingest("runbooks","Lifecycle",text); }
    @Test void documentListIsTenantScopedAndTracksPendingActiveAndDeletedVersions() {
        String tenant="library-"+UUID.randomUUID();
        UUID original=repository.enqueue(tenant,input("library original"));
        repository.enqueue("other-"+tenant,input("private"));
        assertThat(repository.list(tenant,0)).hasSize(1);
        assertThat(repository.list(tenant,0).getFirst().pendingSourceId()).isEqualTo(original);
        assertThat(repository.list(tenant,0).getFirst().activeSourceId()).isNull();
        pipeline.index(original.toString());
        UUID next=repository.replace(tenant,original,input("library replacement"),"replace");
        var pending=repository.list(tenant,0).getFirst();
        assertThat(pending.id()).isEqualTo(next);
        assertThat(pending.activeSourceId()).isEqualTo(original);
        pipeline.index(next.toString());
        assertThat(repository.list(tenant,0).getFirst().activeSourceId()).isEqualTo(next);
        assertThat(repository.list(tenant,1)).isEmpty();
        assertThatThrownBy(()->repository.list(tenant,-1)).hasMessageContaining("400");
        repository.delete(tenant,next);
        assertThat(repository.list(tenant,0)).isEmpty();
    }
}
