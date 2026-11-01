package dev.rohit.rag;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class Embeddings {
    private final EmbeddingModel model;
    private final LocalCache cache;
    private final MeterRegistry metrics;
    private final String modelName;
    public Embeddings(EmbeddingModel model, LocalCache cache, MeterRegistry metrics,
                      @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text:v1.5}") String modelName) {
        if(!"nomic-embed-text:v1.5".equals(modelName))
            throw new IllegalArgumentException("This index requires nomic-embed-text:v1.5. A different embedding model requires a new index and a data migration.");
        this.model=model; this.cache=cache; this.metrics=metrics; this.modelName=modelName;
    }
    public float[] embed(String tenant,String text) {
        String key=tenant+":"+modelName+":"+TextChunker.id(text);
        float[] cached=cache.get("embedding",key,float[].class);
        if(cached!=null) return cached;
        float[] result=metrics.timer("rag.embedding.duration").record(() -> model.embed(text));
        if(result==null || result.length!=768) throw new IllegalStateException("Expected 768 embedding dimensions");
        cache.put("embedding",key,result,Duration.ofDays(7)); return result;
    }
    public static String vector(float[] values) { return java.util.Arrays.toString(values); }
}
