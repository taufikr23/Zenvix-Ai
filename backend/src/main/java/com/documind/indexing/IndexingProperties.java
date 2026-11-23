package com.documind.indexing;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.indexing")
public record IndexingProperties(boolean enabled, String apiKey,
        @Min(1000) @Max(120000) int timeoutMs,
        @Min(1) long dailyCharacters, @Min(1) long userDailyCharacters) {
    public static final String MODEL = "gemini-embedding-001";
    public static final int DIMENSIONS = 768;
    @AssertTrue(message = "GEMINI_API_KEY wajib terisi saat EMBEDDING_ENABLED=true.")
    public boolean isKeyConfigured() { return !enabled || (apiKey != null && !apiKey.isBlank()); }
    @Override public String toString() { return "IndexingProperties[enabled=" + enabled + ",apiKey=REDACTED]"; }
}
