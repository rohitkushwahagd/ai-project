package dev.rohit.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Response reuse is valid only for the same tenant, corpus, model and generation settings. */
@Component
public record QueryCacheKey(
    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:1.5b}") String chatModel,
    @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text:v1.5}") String embeddingModel,
    @Value("${spring.ai.ollama.chat.options.temperature:0.0}") double temperature,
    @Value("${spring.ai.ollama.chat.options.num-ctx:4096}") int contextWindow,
    @Value("${spring.ai.ollama.chat.options.num-predict:512}") int outputLimit,
    @Value("${app.retrieval.exact:true}") boolean exactRetrieval) {

    public String forQuestion(String tenant,long revision,String question) {
        // Bump this version when the prompt, chunking or reranking implementation changes.
        return tenant+":pipeline-v4:"+TextChunker.id(toString())+":"+revision+":"+TextChunker.id(question);
    }
}
