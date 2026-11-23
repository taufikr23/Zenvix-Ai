package com.documind.chat;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated
@ConfigurationProperties("app.chat")
public record ChatProperties(boolean enabled,
        @NotBlank @Pattern(regexp="gemini-[a-zA-Z0-9.-]+") String model,
        @Min(1000) @Max(120000) int timeoutMs,
        @Min(256) @Max(4096) int maxOutputTokens,
        @Min(1) @Max(8) int topK, @DecimalMin("0") @DecimalMax("1") double minSimilarity,
        @Min(1000) @Max(12000) int maxContextCharacters,
        @Min(1) @Max(100) int maxTurns, @Min(1) @Max(200) int maxConversations,
        @Min(1) long dailyRequests, @Min(1) long userDailyRequests,
        @Min(1000) long dailyCharacters, @Min(1000) long userDailyCharacters) {}
