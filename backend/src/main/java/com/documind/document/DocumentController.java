package com.documind.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;
    private final com.documind.processing.ChunkRepository chunks;
    private final com.documind.indexing.IndexingService indexing;
    public DocumentController(DocumentService documents, com.documind.processing.ChunkRepository chunks,
                              com.documind.indexing.IndexingService indexing) {
        this.documents = documents; this.chunks = chunks; this.indexing = indexing;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestPart("file") MultipartFile file) {
        var result = documents.upload(owner(jwt), file);
        return ResponseEntity.created(URI.create("/api/documents/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping
    public ResponseEntity<DocumentPage> list(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(documents.list(owner(jwt), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.get(owner(jwt), id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var result = documents.download(owner(jwt), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(result.document().mediaType()))
                .contentLength(result.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.document().filename(), StandardCharsets.UTF_8).build().toString())
                .body(result.bytes());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documents.delete(owner(jwt), id);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{id}/process")
    public ResponseEntity<DocumentResponse> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(documents.retry(owner(jwt), id));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<com.documind.processing.ChunkRepository.ChunkPage> chunks(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new com.documind.common.ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "page harus 0–100000 dan size 1–100.");
        }
        var metadata = documents.get(owner(jwt), id);
        if (metadata.status().equals("DELETING")) {
            throw new com.documind.common.ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Dokumen tidak ditemukan.");
        }
        if (!java.util.Set.of("EXTRACTED","READY","INDEX_FAILED").contains(metadata.status())) {
            throw new com.documind.common.ApiException(HttpStatus.CONFLICT, "TEXT_NOT_READY",
                    "Teks dokumen belum tersedia. Periksa status dokumen.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new com.documind.processing.ChunkRepository.ChunkPage(
                        chunks.list(id, owner(jwt), page, size), page, size, metadata.chunkCount()));
    }


    @PostMapping("/{id}/index")
    public ResponseEntity<DocumentResponse> retryIndex(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(indexing.retry(owner(jwt),id));
    }

    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
