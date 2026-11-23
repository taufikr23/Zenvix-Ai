package com.documind.document;

import com.documind.common.ApiException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final FileValidator validator;
    private final FileStorage storage;
    private final StorageProperties properties;
    private final TransactionTemplate transaction;

    public DocumentService(DocumentRepository documents, FileValidator validator, FileStorage storage,
                           StorageProperties properties, org.springframework.transaction.PlatformTransactionManager manager) {
        this.documents = documents;
        this.validator = validator;
        this.storage = storage;
        this.properties = properties;
        this.transaction = new TransactionTemplate(manager);
    }

    public DocumentResponse upload(UUID owner, MultipartFile upload) {
        ValidatedFile file = validator.validate(upload);
        UUID id = UUID.randomUUID();
        var saved = new AtomicBoolean(false);
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                if (!documents.lockActiveOwner(owner)) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Akun tidak aktif.");
                }
                if (documents.countOwned(owner) >= properties.maxDocumentsPerUser()) {
                    throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_LIMIT",
                            "Batas jumlah dokumen tercapai. Hapus dokumen sebelum mengunggah kembali.");
                }
                try { storage.save(id, file.bytes()); saved.set(true); }
                catch (IOException exception) { throw storageError(); }
                return DocumentResponse.from(documents.create(id, owner, file));
            }));
        } catch (RuntimeException exception) {
            if (saved.get()) {
                try { storage.delete(id); }
                catch (IOException cleanup) {
                    LoggerFactory.getLogger(DocumentService.class).error(
                            "File upload tanpa metadata perlu dibersihkan; documentId={}", id);
                }
            }
            throw exception;
        }
    }

    public DocumentPage list(UUID owner, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page harus 0–100000 dan size 1–100.");
        }
        return new DocumentPage(documents.listOwned(owner, page, size).stream()
                .map(DocumentResponse::from).toList(), page, size, documents.countOwned(owner));
    }

    public DocumentResponse get(UUID owner, UUID id) {
        return DocumentResponse.from(owned(owner, id));
    }

    public Download download(UUID owner, UUID id) {
        DocumentRecord document = owned(owner, id);
        if (document.status().equals("DELETING")) { throw notFound(); }
        try {
            byte[] bytes = storage.read(id);
            if (bytes.length != document.sizeBytes()) { throw storageError(); }
            return new Download(DocumentResponse.from(document), bytes);
        } catch (NoSuchFileException exception) { throw notFound(); }
        catch (IOException exception) { throw storageError(); }
    }

    public void delete(UUID owner, UUID id) {
        // Tombstone disimpan dahulu: kegagalan filesystem dapat dicoba ulang dengan DELETE yang sama.
        documents.markDeleting(id, owner).orElseThrow(this::notFound);
        try { storage.delete(id); }
        catch (IOException exception) { throw storageError(); }
        documents.deleteOwned(id, owner);
    }


    public DocumentResponse retry(UUID owner, UUID id) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var document = documents.lockOwned(id, owner).orElseThrow(this::notFound);
            if (document.status().equals("DELETING")) { throw notFound(); }
            if (document.status().equals("QUEUED")) { return DocumentResponse.from(document); }
            if (!document.status().equals("FAILED")) {
                throw new ApiException(HttpStatus.CONFLICT, "PROCESSING_NOT_RETRYABLE",
                        "Hanya dokumen yang gagal diproses yang dapat dicoba ulang.");
            }
            return DocumentResponse.from(documents.queue(id, owner));
        }));
    }

    private DocumentRecord owned(UUID owner, UUID id) {
        return documents.findOwned(id, owner).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Dokumen tidak ditemukan.");
    }

    private ApiException storageError() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                "Penyimpanan dokumen sementara tidak tersedia. Coba kembali.");
    }

    public record Download(DocumentResponse document, byte[] bytes) {
        @Override public String toString() { return "Download[id=" + document.id() + "]"; }
    }
}
