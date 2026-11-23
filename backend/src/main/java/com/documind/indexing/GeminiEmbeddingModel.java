package com.documind.indexing;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;

/**
 * Adapter Spring AI: versi 1.1.8 belum meneruskan taskType pada model bawaannya.
 * Satu input per request membuat kuota dan pemulihan setiap chunk jelas.
 */
public class GeminiEmbeddingModel implements EmbeddingModel {
    private final Client client;
    public GeminiEmbeddingModel(Client client) { this.client = client; }

    @Override public EmbeddingResponse call(EmbeddingRequest request) {
        if (request.getInstructions() == null || request.getInstructions().size() != 1 ||
                request.getInstructions().getFirst().isBlank()) {
            throw new IndexingFailure("INVALID_EMBEDDING_INPUT");
        }

        String task = "RETRIEVAL_DOCUMENT";
        if (request.getOptions() instanceof org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions options
                && options.getTaskType() != null) {
            task = options.getTaskType().name();
        }
        if (!java.util.Set.of("RETRIEVAL_DOCUMENT","RETRIEVAL_QUERY").contains(task)) {
            throw new IndexingFailure("INVALID_EMBEDDING_INPUT");
        }
        var response = client.models.embedContent(IndexingProperties.MODEL, request.getInstructions().getFirst(),
                EmbedContentConfig.builder().taskType(task)
                        .outputDimensionality(IndexingProperties.DIMENSIONS).build());
        var vectors = response.embeddings().orElseThrow(() -> new IndexingFailure("INVALID_EMBEDDING"));
        if (vectors.size() != 1) { throw new IndexingFailure("INVALID_EMBEDDING"); }
        var embedding = vectors.getFirst();
        if (embedding.statistics().isPresent() && embedding.statistics().get().truncated().orElse(false)) {
            throw new IndexingFailure("EMBEDDING_INPUT_TRUNCATED");
        }
        var values = embedding.values().orElseThrow(() -> new IndexingFailure("INVALID_EMBEDDING"));
        float[] vector = new float[values.size()];
        for (int i=0; i<vector.length; i++) { vector[i] = values.get(i); }
        return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
    }

    @Override public float[] embed(Document document) { return embed(document.getText()); }
    @Override public int dimensions() { return IndexingProperties.DIMENSIONS; }
}
