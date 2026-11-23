package com.documind;
import com.documind.chat.*;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GeminiChatAdapterTest {
    private ChatProperties properties() { return new ChatProperties(true,"gemini-3.5-flash-lite",2000,1024,5,0.45,6000,50,100,500,50,2000000,200000); }
    @Test void springAiSendsJsonModeAndSystemMessageUsingConfiguredModel() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<String>();var route=new AtomicReference<String>();
        server.createContext("/",exchange->{
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            route.set(exchange.getRequestURI().getPath());
            byte[] result="""
                    {"modelVersion":"gemini-3.5-flash-lite","candidates":[{"content":{"role":"model","parts":[{"text":"{\\"found\\":false,\\"passages\\":[]}"}]},"finishReason":"STOP"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,result.length);exchange.getResponseBody().write(result);exchange.close();
        });
        server.start();
        try(var client=Client.builder().apiKey("test-only-key").vertexAI(false).httpOptions(HttpOptions.builder()
                .baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).timeout(2000)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()){
            var model=new ChatConfig().chatModel(client,properties());
            var response=model.call(new org.springframework.ai.chat.prompt.Prompt(java.util.List.of(
                    new org.springframework.ai.chat.messages.SystemMessage("Hanya sumber dokumen."),
                    org.springframework.ai.chat.messages.UserMessage.builder().text("Pertanyaan uji")
                            .media(new org.springframework.ai.content.Media(org.springframework.util.MimeTypeUtils.IMAGE_PNG,
                                    new org.springframework.core.io.ByteArrayResource(new byte[]{1,2,3}))).build())));
            assertThat(response.getResult().getOutput().getText()).contains("\"found\":false");
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body.get());
            assertThat(json.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
            assertThat(json.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(1024);
            assertThat(json.path("generationConfig").has("temperature")).isFalse();
            assertThat(json.path("generationConfig").has("topP")).isFalse();
            assertThat(json.path("generationConfig").has("topK")).isFalse();
            assertThat(json.path("generationConfig").has("candidateCount")).isFalse();
            assertThat(body.get()).contains("Hanya sumber dokumen.","Pertanyaan uji").doesNotContain("googleSearch");
            assertThat(body.get()).contains("inlineData","image/png","AQID");
            assertThat(route.get()).endsWith("/models/gemini-3.5-flash-lite:generateContent");
        }finally{server.stop(0);}
    }
    @Test void quotaFailureDoesNotTriggerSpringOrSdkRetries() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/",exchange->{
            calls.incrementAndGet();exchange.getRequestBody().readAllBytes();
            byte[] result="{\"error\":{\"code\":429,\"message\":\"test quota\",\"status\":\"RESOURCE_EXHAUSTED\"}}".getBytes();
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(429,result.length);exchange.getResponseBody().write(result);exchange.close();
        });server.start();
        try(var client=Client.builder().apiKey("test-only-key").vertexAI(false).httpOptions(HttpOptions.builder()
                .baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).timeout(2000)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build()){
            var model=new ChatConfig().chatModel(client,properties());
            assertThatThrownBy(()->model.call(new org.springframework.ai.chat.prompt.Prompt("uji"))).isInstanceOf(RuntimeException.class);
            assertThat(calls.get()).isEqualTo(1);
        }finally{server.stop(0);}
    }
}
