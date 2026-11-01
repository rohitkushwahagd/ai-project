package dev.rohit.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class LocalCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final MeterRegistry metrics;
    public LocalCache(StringRedisTemplate redis, ObjectMapper json, MeterRegistry metrics) {
        this.redis=redis; this.json=json; this.metrics=metrics;
    }
    public <T> T get(String kind, String key, Class<T> type) {
        try {
            String value=redis.opsForValue().get(kind+":"+key);
            metrics.counter("rag.cache", "kind", kind, "result", value == null ? "miss":"hit").increment();
            return value == null ? null : json.readValue(value,type);
        } catch (Exception ex) { metrics.counter("rag.cache.errors").increment(); return null; }
    }
    public void put(String kind,String key,Object value,Duration ttl) {
        try { redis.opsForValue().set(kind+":"+key,json.writeValueAsString(value),ttl); }
        catch (Exception ex) { metrics.counter("rag.cache.errors").increment(); }
    }
}
