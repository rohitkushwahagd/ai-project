package dev.rohit.rag;

import java.util.*;
import java.util.stream.Collectors;
import dev.rohit.rag.ApiTypes.Evidence;

public final class Reranker {
    private Reranker() {}
    // Transparent hybrid baseline, not a trained cross-encoder. Evaluate before changing weights.
    public static List<Evidence> rank(String query,List<Evidence> candidates,int k) {
        Set<String> terms=terms(query);
        return candidates.stream().filter(e->e.score()>=0.3).map(e->{
            Set<String> words=terms(e.title()+" "+e.text());
            long matches=terms.stream().filter(words::contains).count();
            double score=.8*e.score()+.2*matches/Math.max(1,terms.size());
            return new Evidence(e.chunkId(),e.sourceId(),e.title(),e.text(),score);
        }).sorted(Comparator.comparingDouble(Evidence::score).reversed().thenComparing(Evidence::chunkId))
          .limit(k).toList();
    }
    private static Set<String> terms(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t->t.length()>2).collect(Collectors.toSet());
    }
}
