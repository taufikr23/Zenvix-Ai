package com.documind.document;

import java.time.Instant;
import java.util.UUID;

/** Lokasi file di server tidak pernah dimasukkan ke DTO publik. */
public record DocumentResponse(UUID id, String filename, String mediaType, String fileType,
                               long sizeBytes, String status, Instant uploadedAt, int chunkCount,
                               int extractedCharacters, Integer sourcePages, Instant processedAt, String processingError,
                               int indexedChunkCount, Instant indexedAt, String indexingError) {
    public static DocumentResponse from(DocumentRecord document) {
        return new DocumentResponse(document.id(), document.originalFilename(), document.mediaType(),
                document.fileType(), document.sizeBytes(), document.status(), document.createdAt(), document.chunkCount(),
                document.extractedCharacters(), document.sourcePages(), document.processedAt(), document.processingError(),
                document.indexedChunkCount(), document.indexedAt(), document.indexingError());
    }
}
