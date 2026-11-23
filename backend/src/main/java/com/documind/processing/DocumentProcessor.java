package com.documind.processing;

import com.documind.document.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DocumentProcessor {
    private final DocumentRepository documents;
    private final FileStorage storage;
    private final DocumentExtractor extractor;
    private final TextChunker chunker;
    private final ChunkRepository chunks;
    private final TransactionTemplate transaction;

    public DocumentProcessor(DocumentRepository documents, FileStorage storage, DocumentExtractor extractor,
            TextChunker chunker, ChunkRepository chunks, org.springframework.transaction.PlatformTransactionManager manager) {
        this.documents = documents; this.storage = storage; this.extractor = extractor;
        this.chunker = chunker; this.chunks = chunks;
        transaction = new TransactionTemplate(manager);
    }

    /** Lock dan commit yang sama mencegah hasil muncul lagi setelah dokumen dihapus. */
    public boolean processNext() {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var next = documents.lockNextQueued();
            if (next.isEmpty()) { return false; }
            var document = next.get();
            documents.markProcessing(document.id());
            ExtractedText text;
            java.util.List<TextChunker.Chunk> result;
            try {
                byte[] bytes = storage.read(document.id());
                if (bytes.length != document.sizeBytes() ||
                        !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(document.sha256())) {
                    throw new ExtractionFailure("FILE_INTEGRITY_ERROR");
                }
                text = extractor.extract(document.fileType(), bytes);
                result = chunker.split(text);
            } catch (ExtractionFailure failure) {
                documents.markFailed(document.id(), failure.code());
                return true;
            } catch (IOException failure) {
                documents.markFailed(document.id(), "FILE_UNAVAILABLE");
                return true;
            } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
            // Kesalahan database harus me-rollback seluruh transaksi; jangan diubah menjadi FAILED.
            chunks.replace(document.id(), document.ownerId(), result);
            int characters = text.sections().stream().mapToInt(section ->
                    section.text().codePointCount(0, section.text().length())).sum();
            documents.markExtracted(document.id(), result.size(), characters, text.sourcePages());
            return true;
        }));
    }
}
