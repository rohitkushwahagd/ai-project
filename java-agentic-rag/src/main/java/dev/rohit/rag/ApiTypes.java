package dev.rohit.rag;

import jakarta.validation.constraints.*;
import java.util.*;

public final class ApiTypes {
    private ApiTypes() {}
    public record Ingest(@NotBlank @Pattern(regexp="docs|tickets|runbooks") String kind,
                         @NotBlank @Size(max=120) String title, @NotBlank @Size(max=100000) String text) {}
    public record Question(@NotBlank @Size(max=1000) String question, boolean tools, boolean bypassCache, boolean allowEscalation) {}
    public record Feedback(@NotNull UUID queryId, @Min(1) @Max(5) int rating, @Size(max=2000) String comment) {}
    public record Source(UUID id, String kind, String title, String content, String status) {}
    public record DocumentSummary(UUID id, String kind, String title, String status, UUID activeSourceId, UUID pendingSourceId) {}
    public record Evidence(UUID chunkId, UUID sourceId, String title, String text, double score) {}
    public record ToolTrace(String tool, boolean error, String result) {}
    public record Result(UUID queryId, String answer, List<Evidence> sources, boolean cached,
                         long latencyMs, Long inputTokens, Long outputTokens, List<ToolTrace> toolTrace) {}
}
