package com.documind.indexing;

import com.documind.common.ApiException;
import com.documind.document.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

@Service
public class IndexingService {
    private final IndexingProperties properties;
    private final IndexRepository indexes;
    private final DocumentRepository documents;
    private final EmbeddingGateway gateway;
    private final EmbeddingBudget budget;
    private final TransactionTemplate transaction;
    public IndexingService(IndexingProperties properties, IndexRepository indexes, DocumentRepository documents,
            EmbeddingGateway gateway, EmbeddingBudget budget, org.springframework.transaction.PlatformTransactionManager manager) {
        this.properties=properties; this.indexes=indexes; this.documents=documents; this.gateway=gateway;
        this.budget=budget; this.transaction=new TransactionTemplate(manager);
    }

    public boolean processNext() {
        if (!properties.enabled()) { return false; }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var optional = indexes.lockNext();
            if (optional.isEmpty()) { return false; }
            var job = optional.get();
            var input = indexes.nextChunk(job);
            if (input.isEmpty()) {
                indexes.failed(job,"NO_CHUNKS_TO_INDEX");
                return true;
            }
            float[] vector;
            try {
                budget.reserve(job.owner(),input.get().characters());
                vector=gateway.embed(input.get().content());
            } catch (IndexingFailure failure) {
                indexes.failed(job,failure.code());
                return true;
            }
            indexes.save(job,input.get().id(),vector);
            indexes.refresh(job);
            return true;
        }));
    }

    public DocumentResponse retry(UUID owner, UUID id) {
        return transaction.execute(status -> {
            var document=documents.lockOwned(id,owner).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","Dokumen tidak ditemukan."));
            if (document.status().equals("DELETING")) {
                throw new ApiException(HttpStatus.NOT_FOUND,"DOCUMENT_NOT_FOUND","Dokumen tidak ditemukan.");
            }
            if (!properties.enabled()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"EMBEDDING_DISABLED",
                        "Embedding belum diaktifkan oleh pemilik aplikasi.");
            }
            if (!java.util.Set.of("EXTRACTED","INDEX_FAILED").contains(document.status())) {
                throw new ApiException(HttpStatus.CONFLICT,"INDEX_NOT_RETRYABLE",
                        "Dokumen harus selesai diekstrak atau gagal indexing.");
            }
            return DocumentResponse.from(documents.queueIndex(id,owner));
        });
    }
}
