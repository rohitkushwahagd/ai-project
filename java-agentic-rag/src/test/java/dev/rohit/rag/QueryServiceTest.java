package dev.rohit.rag;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.rohit.rag.ApiTypes.*;

class QueryServiceTest {
    private final SourceRepository repository=mock(SourceRepository.class);
    private final Embeddings embeddings=mock(Embeddings.class);
    private final LocalCache cache=mock(LocalCache.class);
    private final ChatModel model=mock(ChatModel.class);
    private final AgentTools tools=mock(AgentTools.class);
    private final QueryCacheKey keys=new QueryCacheKey("qwen2.5:3b","nomic-embed-text:v1.5",0,8192,1024,true);
    private final QueryService service=new QueryService(repository,embeddings,cache,ChatClient.builder(model),
        tools,new SimpleMeterRegistry(),keys);
    private final TenantAccess access=new TenantAccess("alpha","token",false);

    @Test void cacheHitReusesEvidenceButCreatesANewQueryAndChargesNoGenerationTokens() {
        var saved=new Result(UUID.randomUUID(),"Answer",List.of(),false,100,20L,8L,List.of());
        when(repository.revision("alpha")).thenReturn(4L);
        when(cache.get("query",keys.forQuestion("alpha",4,"Question"),Result.class)).thenReturn(saved);
        var tokens=new ArrayList<String>();
        var result=service.answer(access,new Question("Question",false,false,false),s->{},tokens::add);
        assertThat(result.cached()).isTrue();
        assertThat(result.queryId()).isNotEqualTo(saved.queryId());
        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
        assertThat(tokens).containsExactly("Answer");
        verify(repository).log(result,"alpha");
        verifyNoInteractions(embeddings,tools);
        verify(model,never()).stream(any(Prompt.class));
    }

    @Test void emptyRetrievalRefusesWithoutInvokingTheChatModel() {
        var vector=new float[768];
        when(embeddings.embed("alpha","Missing fact")).thenReturn(vector);
        when(repository.search("alpha",vector,20)).thenReturn(List.of());
        var result=service.answer(access,new Question("Missing fact",false,true,false),s->{},t->{});
        assertThat(result.answer()).contains("don't have enough information");
        assertThat(result.sources()).isEmpty();
        verifyNoInteractions(cache,tools);
        verify(model,never()).stream(any(Prompt.class));
    }

    @Test void streamsAnswerWithItsRetrievedEvidenceAndBypassesResponseCache() {
        var vector=new float[768];
        var source=new Evidence(UUID.randomUUID(),UUID.randomUUID(),"Orion","Orion 17 minutes Aurora",.95);
        when(embeddings.embed("alpha","Orion policy")).thenReturn(vector);
        when(repository.search("alpha",vector,20)).thenReturn(List.of(source));
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
            new ChatResponse(List.of(new Generation(new AssistantMessage("17 minutes. ")))),
            new ChatResponse(List.of(new Generation(new AssistantMessage("Aurora."))))));
        var tokens=new ArrayList<String>(); var evidenceEvents=new ArrayList<Object>();
        var result=service.answer(access,new Question("Orion policy",false,true,false),evidenceEvents::add,tokens::add);
        assertThat(tokens).containsExactly("17 minutes. ","Aurora.");
        assertThat(result.answer()).isEqualTo("17 minutes. Aurora.");
        assertThat(result.sources()).extracting(Evidence::sourceId).containsExactly(source.sourceId());
        assertThat(evidenceEvents).hasSize(1);
        verifyNoInteractions(cache,tools);
        verify(repository).log(result,"alpha");
    }

    @Test void responseReuseIsIsolatedAcrossTenantsCorpusAndInferenceSettings() {
        String original=keys.forQuestion("alpha",1,"question");
        assertThat(keys.forQuestion("beta",1,"question")).isNotEqualTo(original);
        assertThat(keys.forQuestion("alpha",2,"question")).isNotEqualTo(original);
        assertThat(new QueryCacheKey("other-chat","nomic-embed-text:v1.5",0,8192,1024,true)
            .forQuestion("alpha",1,"question")).isNotEqualTo(original);
        assertThat(new QueryCacheKey("qwen2.5:3b","nomic-embed-text:v1.5",0,8192,1024,false)
            .forQuestion("alpha",1,"question")).isNotEqualTo(original);
        assertThat(new QueryCacheKey("qwen2.5:3b","nomic-embed-text:v1.5",.7,8192,1024,true)
            .forQuestion("alpha",1,"question")).isNotEqualTo(original);
    }
}
