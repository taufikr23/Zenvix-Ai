package com.documind.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentRecord(UUID id, UUID ownerId, String originalFilename, String mediaType,
                             String fileType, long sizeBytes, String sha256, String status,
                             Instant createdAt, int chunkCount, int extractedCharacters, Integer sourcePages,
                             Instant processedAt, String processingError, int indexedChunkCount,
                             Instant indexedAt, String indexingError) {}
