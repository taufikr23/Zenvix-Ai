package com.documind.indexing;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IndexRepository {
    private final JdbcTemplate jdbc;
    public IndexRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Job> lockNext() {
        return jdbc.query("""
                SELECT d.id,d.owner_id FROM documents d JOIN users u ON u.id=d.owner_id
                WHERE d.status='EXTRACTED' AND u.enabled=true
                ORDER BY d.created_at,d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
                """, (row,index) -> new Job(row.getObject("id",UUID.class),row.getObject("owner_id",UUID.class)))
                .stream().findFirst();
    }

    public Optional<Input> nextChunk(Job job) {
        return jdbc.query("""
                SELECT id,content,character_count FROM document_chunks
                WHERE document_id=? AND owner_id=? AND embedding IS NULL ORDER BY chunk_index LIMIT 1
                """, (row,index) -> new Input(row.getObject("id",UUID.class),row.getString("content"),
                        row.getInt("character_count")),job.id(),job.owner()).stream().findFirst();
    }

    public void save(Job job, UUID chunk, float[] vector) {
        int changed = jdbc.update("""
                UPDATE document_chunks SET embedding=?::vector,embedding_model=?
                WHERE id=? AND document_id=? AND owner_id=? AND embedding IS NULL
                """, Arrays.toString(vector),IndexingProperties.MODEL,chunk,job.id(),job.owner());
        if (changed != 1) { throw new IllegalStateException("Chunk berubah saat indexing."); }
    }

    public void refresh(Job job) {
        jdbc.update("""
                UPDATE documents d SET
                    indexed_chunk_count=(SELECT count(*) FROM document_chunks c WHERE c.document_id=d.id AND c.embedding IS NOT NULL),
                    status=CASE WHEN NOT EXISTS (SELECT 1 FROM document_chunks c WHERE c.document_id=d.id AND c.embedding IS NULL)
                        AND EXISTS (SELECT 1 FROM document_chunks c WHERE c.document_id=d.id) THEN 'READY' ELSE 'EXTRACTED' END,
                    indexed_at=CASE WHEN NOT EXISTS (SELECT 1 FROM document_chunks c WHERE c.document_id=d.id AND c.embedding IS NULL)
                        AND EXISTS (SELECT 1 FROM document_chunks c WHERE c.document_id=d.id) THEN CURRENT_TIMESTAMP ELSE NULL END,
                    indexing_error=NULL
                WHERE d.id=? AND d.owner_id=?
                """,job.id(),job.owner());
    }

    public void failed(Job job, String code) {
        jdbc.update("UPDATE documents SET status='INDEX_FAILED',indexing_error=? WHERE id=? AND owner_id=?",
                code,job.id(),job.owner());
    }

    public record Job(UUID id, UUID owner) {}
    public record Input(UUID id, String content, int characters) {
        @Override public String toString() { return "Input[id="+id+"]"; }
    }
}
