package com.documind.indexing;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingGateway {
    private final ObjectProvider<EmbeddingModel> models;
    public EmbeddingGateway(ObjectProvider<EmbeddingModel> models) { this.models = models; }

    public float[] embed(String text) {
        var model = models.getIfAvailable();
        if (model == null) { throw new IndexingFailure("EMBEDDING_DISABLED"); }
        try { return normalize(model.embed(text)); }
        catch (IndexingFailure failure) { throw failure; }
        catch (RuntimeException failure) {
            // Provider messages can contain request data. Only a stable code is exposed.
            throw new IndexingFailure("EMBEDDING_PROVIDER_ERROR");
        }
    }


    public float[] embedQuery(String text) {
        var model = models.getIfAvailable();
        if (model == null) { throw new IndexingFailure("EMBEDDING_DISABLED"); }
        try {
            var options = org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.builder()
                    .model(IndexingProperties.MODEL).dimensions(IndexingProperties.DIMENSIONS)
                    .taskType(org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY)
                    .build();
            var response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(java.util.List.of(text),options));
            if (response == null || response.getResults().size()!=1) { throw new IndexingFailure("INVALID_EMBEDDING"); }
            return normalize(response.getResult().getOutput());
        } catch (IndexingFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw new IndexingFailure("EMBEDDING_PROVIDER_ERROR"); }
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length != IndexingProperties.DIMENSIONS) {
            throw new IndexingFailure("INVALID_EMBEDDING");
        }
        double sum = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) { throw new IndexingFailure("INVALID_EMBEDDING"); }
            sum += (double) value * value;
        }
        if (sum == 0 || !Double.isFinite(sum)) { throw new IndexingFailure("INVALID_EMBEDDING"); }
        double norm = Math.sqrt(sum);
        float[] result = new float[vector.length];
        for (int i=0; i<vector.length; i++) { result[i] = (float)(vector[i]/norm); }
        return result;
    }
}
