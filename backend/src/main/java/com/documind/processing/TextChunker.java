package com.documind.processing;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
    private final ProcessingProperties properties;
    public TextChunker(ProcessingProperties properties) { this.properties = properties; }

    public List<Chunk> split(ExtractedText extracted) {
        var chunks = new ArrayList<Chunk>();
        for (var section : extracted.sections()) {
            int[] points = section.text().codePoints().toArray();
            for (int start = 0; start < points.length;) {
                int end = Math.min(start + properties.chunkSize(), points.length);
                String content = new String(points, start, end - start);
                if (!content.isBlank()) {
                    if (chunks.size() >= properties.maxChunks()) { throw new ExtractionFailure("TOO_MANY_CHUNKS"); }
                    chunks.add(new Chunk(chunks.size(), section.number(), section.pageNumber(), content, end - start));
                }
                if (end == points.length) { break; }
                start = end - properties.chunkOverlap();
            }
        }
        if (chunks.isEmpty()) { throw new ExtractionFailure("NO_EXTRACTABLE_TEXT"); }
        return List.copyOf(chunks);
    }

    public record Chunk(int index, int sourceSection, Integer pageNumber, String content, int characterCount) {
        @Override public String toString() { return "Chunk[index=" + index + "]"; }
    }
}
