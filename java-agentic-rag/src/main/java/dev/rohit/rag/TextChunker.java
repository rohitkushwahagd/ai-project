package dev.rohit.rag;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class TextChunker {
    private TextChunker() {}
    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        // Bounded code-point windows avoid splitting a surrogate pair. Token-aware splitting can replace this later.
        int[] points = text.codePoints().toArray();
        for (int start=0; start<points.length; start+=1000) {
            int count = Math.min(1200, points.length-start);
            result.add(new String(points, start, count));
            if (start+count == points.length) break;
        }
        return result;
    }
    public static UUID id(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
}
