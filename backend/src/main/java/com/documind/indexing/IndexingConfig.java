package com.documind.indexing;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(IndexingProperties.class)
public class IndexingConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name="app.indexing.enabled", havingValue="true")
    public Client embeddingClient(IndexingProperties properties) {
        return Client.builder().apiKey(properties.apiKey()).vertexAI(false)
                .httpOptions(HttpOptions.builder().timeout(properties.timeoutMs())
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }

    @Bean
    @ConditionalOnProperty(name="app.indexing.enabled", havingValue="true")
    public EmbeddingModel embeddingModel(@org.springframework.beans.factory.annotation.Qualifier("embeddingClient") Client embeddingClient) {
        return new GeminiEmbeddingModel(embeddingClient);
    }
}
