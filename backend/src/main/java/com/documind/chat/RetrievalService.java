package com.documind.chat;
import com.documind.common.ApiException;
import com.documind.document.*;
import com.documind.indexing.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class RetrievalService {
    private final ChatProperties properties;
    private final DocumentRepository documents;
    private final RetrievalRepository repository;
    private final EmbeddingGateway gateway;
    private final EmbeddingBudget budget;
    private final ChatConcurrency concurrency;
    private final TransactionTemplate transaction;
    public RetrievalService(ChatProperties properties,DocumentRepository documents,RetrievalRepository repository,
            EmbeddingGateway gateway,EmbeddingBudget budget,ChatConcurrency concurrency,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.properties=properties;this.documents=documents;this.repository=repository;this.gateway=gateway;
        this.budget=budget;this.concurrency=concurrency;this.transaction=new TransactionTemplate(manager);
    }
    public List<RetrievalRepository.Hit> search(UUID owner,UUID document,String query) {
        return concurrency.execute(()->transaction.execute(status->{
            lockReady(owner,document); requireEnabled(); return retrieve(owner,document,query.strip());
        }));
    }
    public DocumentRecord lockReady(UUID owner,UUID document) {
        var record=documents.lockOwned(document,owner).orElseThrow(ChatErrors::missing);
        if(record.status().equals("DELETING"))throw ChatErrors.missing();
        if(!record.status().equals("READY"))throw new ApiException(HttpStatus.CONFLICT,"DOCUMENT_NOT_READY","Dokumen belum selesai diindeks.");
        return record;
    }
    public void requireEnabled() {
        if(!properties.enabled())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CHAT_DISABLED","Fitur pencarian dan chat belum diaktifkan.");
    }
    /** Dipanggil ketika lock dokumen sudah dipegang oleh transaksi caller. */
    public List<RetrievalRepository.Hit> retrieve(UUID owner,UUID document,String query) {
        try {
            budget.reserve(owner,query.codePointCount(0,query.length()));
            var hits=repository.search(owner,document,gateway.embedQuery(query),properties.topK(),properties.minSimilarity());
            var result=new ArrayList<RetrievalRepository.Hit>();int size=0;
            for(var hit:hits){
                if(size+hit.content().length()>properties.maxContextCharacters())continue;
                result.add(hit);size+=hit.content().length();
            }
            return List.copyOf(result);
        }catch(IndexingFailure failure){
            if(failure.code().equals("EMBEDDING_DAILY_LIMIT"))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,failure.code(),"Batas penggunaan embedding harian tercapai.");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"QUERY_EMBEDDING_FAILED","Pencarian AI sementara tidak tersedia.");
        }
    }
}
