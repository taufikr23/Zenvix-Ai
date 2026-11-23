package com.documind;
import com.documind.indexing.*;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GeminiAdapterTest {
    @Test
    void realSdkSendsDocumentTaskDimensionsAndKeyHeader() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var received=new AtomicReference<String>();
        var route=new AtomicReference<String>();
        var key=new AtomicReference<String>();
        server.createContext("/", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            route.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] result="{\"embeddings\":[{\"values\":[3.0,4.0]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,result.length);
            exchange.getResponseBody().write(result); exchange.close();
        });
        server.start();
        try (var client=Client.builder().apiKey("test-only-key").vertexAI(false)
                .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort())
                        .timeout(2000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
            assertThat(new GeminiEmbeddingModel(client).embed("Dokumen uji")).containsExactly(3f,4f);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(received.get());
            var request=json.get("requests").get(0);
            assertThat(request.get("taskType").asText()).isEqualTo("RETRIEVAL_DOCUMENT");
            assertThat(request.get("outputDimensionality").asInt()).isEqualTo(768);
            assertThat(request.get("content").get("parts").get(0).get("text").asText()).isEqualTo("Dokumen uji");
            assertThat(route.get()).endsWith("/models/gemini-embedding-001:batchEmbedContents");
            assertThat(key.get()).isEqualTo("test-only-key");
            var options=org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.builder()
                    .taskType(org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY).build();
            new GeminiEmbeddingModel(client).call(new org.springframework.ai.embedding.EmbeddingRequest(java.util.List.of("Pertanyaan uji"),options));
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(received.get())
                    .get("requests").get(0).get("taskType").asText()).isEqualTo("RETRIEVAL_QUERY");
        } finally { server.stop(0); }
    }

    @Test
    void sdkDoesNotMultiplyAttemptsOnQuotaFailure() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var calls=new AtomicInteger();
        server.createContext("/",exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] error="{\"error\":{\"code\":429,\"message\":\"test quota\",\"status\":\"RESOURCE_EXHAUSTED\"}}".getBytes();
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(429,error.length);exchange.getResponseBody().write(error);exchange.close();
        });
        server.start();
        try (var client=Client.builder().apiKey("test-only-key").vertexAI(false)
                .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort())
                        .timeout(2000).retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()) {
            assertThatThrownBy(() -> new GeminiEmbeddingModel(client).embed("uji")).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void vectorsAreNormalizedAndInvalidVectorsAreRejected() {
        float[] values=new float[768];values[0]=3;values[1]=4;
        var normalized=EmbeddingGateway.normalize(values);
        assertThat(normalized[0]).isCloseTo(0.6f,within(0.00001f));
        assertThat(normalized[1]).isCloseTo(0.8f,within(0.00001f));
        float[] nan=new float[768];nan[0]=Float.NaN;
        float[] infinite=new float[768];infinite[0]=Float.POSITIVE_INFINITY;
        for(float[] invalid:java.util.List.of(new float[5],new float[768],nan,infinite)) {
            assertThatThrownBy(() -> EmbeddingGateway.normalize(invalid)).hasMessage("INVALID_EMBEDDING");
        }
    }

    @Test
    void enabledConfigurationRequiresKeyAndNeverPrintsIt() {
        var disabled=new IndexingProperties(false,"",20000,100,100);
        var empty=new IndexingProperties(true,"",20000,100,100);
        var enabled=new IndexingProperties(true,"not-a-real-secret",20000,100,100);
        assertThat(disabled.isKeyConfigured()).isTrue();
        assertThat(empty.isKeyConfigured()).isFalse();
        assertThat(enabled.isKeyConfigured()).isTrue();
        assertThat(enabled.toString()).doesNotContain("not-a-real-secret");
    }
}
