package com.documind.processing;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.processing")
public record ProcessingProperties(@Min(100) @Max(4000) int chunkSize,
        @Min(0) int chunkOverlap, @Min(100) @Max(5000000) int maxCharacters,
        @Min(1) @Max(10000) int maxChunks, @Min(1) @Max(300) int timeoutSeconds) {
    @AssertTrue(message = "Overlap harus lebih kecil daripada chunk size.")
    public boolean isOverlapValid() { return chunkOverlap < chunkSize; }
}
