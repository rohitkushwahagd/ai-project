package dev.rohit.rag;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.rohit.rag.ApiTypes.*;

class CoreTest {
    @Test void unsupportedEmbeddingModelFailsBeforeUsingTheExistingIndex() {
        assertThatThrownBy(()->new Embeddings(mock(org.springframework.ai.embedding.EmbeddingModel.class),
            mock(LocalCache.class),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),"another-768-model"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("new index");
    }
    @Test void chunkerPreservesUnicodeAndBounds() {
        String text="🚀".repeat(2500);
        var chunks=TextChunker.split(text);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).codePointCount(0,chunks.get(0).length())).isEqualTo(1200);
        assertThat(chunks.get(2).codePointCount(0,chunks.get(2).length())).isEqualTo(500);
        assertThat(chunks.stream().anyMatch(s->s.contains("\uFFFD"))).isFalse();
        assertThat(TextChunker.split("")).isEmpty();
    }
    @Test void rerankerPromotesRelevantLexicalMatchButRejectsWeakVectors() {
        var a=new Evidence(UUID.randomUUID(),UUID.randomUUID(),"Noise","Bananas",.7);
        var b=new Evidence(UUID.randomUUID(),UUID.randomUUID(),"Orion","Orion escalation",.69);
        var weak=new Evidence(UUID.randomUUID(),UUID.randomUUID(),"Orion","Orion escalation",.1);
        assertThat(Reranker.rank("Orion escalation",List.of(a,b,weak),5)).extracting(Evidence::chunkId)
                .containsExactly(b.chunkId(),a.chunkId());
    }
    @Test void missingOrInvalidTenantFailsClosed() {
        var jwt=Jwt.withTokenValue("x").header("alg","HS256").subject("user").claim("tenant_id","../bad").build();
        assertThatThrownBy(()->TenantAccess.from(jwt)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void escalationCannotBeGrantedByToolArguments() {
        var repo=mock(SourceRepository.class); UUID id=UUID.randomUUID();
        when(repo.get("one",id)).thenReturn(new Source(id,"tickets","Demo","text","READY"));
        var service=new ToolService(repo);
        assertThatThrownBy(()->service.call("escalation",Map.of("sourceId",id.toString(),"reason","test","escalate",true),
                new TenantAccess("one","token",false))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(repo,never()).escalate(anyString(),any(),anyString());
    }
    @Test void sameTenantLookupReturnsBoundedStructuredExcerpt() {
        var repo=mock(SourceRepository.class); UUID id=UUID.randomUUID();
        when(repo.get("one",id)).thenReturn(new Source(id,"tickets","Demo","incident text","READY"));
        var result=(Map<?,?>)new ToolService(repo).call("ticket_lookup",Map.of("sourceId",id.toString()),
            new TenantAccess("one","token",false));
        assertThat(result.get("sourceId")).isEqualTo(id);
        assertThat(result.get("kind")).isEqualTo("tickets");
        assertThat(result.get("text")).isEqualTo("incident text");
        assertThat(result.get("truncated")).isEqualTo(false);
        verify(repo).get("one",id);
    }
    @Test void escalationWithScopeIsAIdempotentActionRequest() {
        var repo=mock(SourceRepository.class); UUID source=UUID.randomUUID(), action=UUID.randomUUID();
        when(repo.get("one",source)).thenReturn(new Source(source,"tickets","Demo","text","READY"));
        when(repo.escalate("one",source,"investigate")).thenReturn(action);
        var result=(Map<?,?>)new ToolService(repo).call("escalation",Map.of("sourceId",source.toString(),"reason","investigate"),
            new TenantAccess("one","token",true));
        assertThat(result.get("id")).isEqualTo(action);
        assertThat(result.get("status")).isEqualTo("RECORDED_SYNTHETIC");
        verify(repo).escalate("one",source,"investigate");
    }
}
