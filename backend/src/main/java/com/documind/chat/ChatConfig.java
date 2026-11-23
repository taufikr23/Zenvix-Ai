package com.documind.chat;
import com.documind.indexing.IndexingProperties;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfig {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="app.chat.enabled",havingValue="true")
    public Client chatClient(ChatProperties chat, IndexingProperties embedding) {
        if (embedding.apiKey() == null || embedding.apiKey().isBlank()) {
            throw new IllegalStateException("CHAT_ENABLED membutuhkan GEMINI_API_KEY.");
        }
        return Client.builder().apiKey(embedding.apiKey()).vertexAI(false)
                .httpOptions(HttpOptions.builder().timeout(chat.timeoutMs())
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }
    @Bean
    @ConditionalOnProperty(name="app.chat.enabled",havingValue="true")
    public ChatModel chatModel(@Qualifier("chatClient") Client client, ChatProperties properties) {
        return GoogleGenAiChatModel.builder().genAiClient(client)
                .defaultOptions(GoogleGenAiChatOptions.builder().model(properties.model())
                        .maxOutputTokens(properties.maxOutputTokens())
                        .responseMimeType("application/json").internalToolExecutionEnabled(false).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }
}
