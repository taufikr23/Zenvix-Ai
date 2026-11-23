package com.documind.document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;
    public DocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockActiveOwner(UUID owner) {
        return !jdbc.query("SELECT id FROM users WHERE id=? AND enabled=true FOR UPDATE",
                (row, index) -> row.getObject("id", UUID.class), owner).isEmpty();
    }

    public long countOwned(UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM documents WHERE owner_id=?", Long.class, owner);
    }

    public DocumentRecord create(UUID id, UUID owner, ValidatedFile file) {
        return jdbc.queryForObject("""
                INSERT INTO documents(id,owner_id,original_filename,media_type,file_type,size_bytes,sha256)
                VALUES (?,?,?,?,?,?,?) RETURNING *
                """, this::mapRow, id, owner, file.filename(), file.mediaType(), file.fileType(),
                file.bytes().length, file.sha256());
    }

    public List<DocumentRecord> listOwned(UUID owner, int page, int size) {
        return jdbc.query("""
                SELECT * FROM documents WHERE owner_id=?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, this::mapRow, owner, size, (long) page * size);
    }

    public Optional<DocumentRecord> findOwned(UUID id, UUID owner) {
        return jdbc.query("SELECT * FROM documents WHERE id=? AND owner_id=?",
                this::mapRow, id, owner).stream().findFirst();
    }

    public Optional<DocumentRecord> markDeleting(UUID id, UUID owner) {
        return jdbc.query("UPDATE documents SET status='DELETING' WHERE id=? AND owner_id=? RETURNING *",
                this::mapRow, id, owner).stream().findFirst();
    }

    public void deleteOwned(UUID id, UUID owner) {
        jdbc.update("DELETE FROM documents WHERE id=? AND owner_id=? AND status='DELETING'", id, owner);
    }


    public Optional<DocumentRecord> lockNextQueued() {
        return jdbc.query("""
                SELECT d.* FROM documents d JOIN users u ON u.id=d.owner_id
                WHERE d.status='QUEUED' AND u.enabled=true
                ORDER BY d.created_at,d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
                """, this::mapRow).stream().findFirst();
    }

    public Optional<DocumentRecord> lockOwned(UUID id, UUID owner) {
        return jdbc.query("SELECT * FROM documents WHERE id=? AND owner_id=? FOR UPDATE",
                this::mapRow, id, owner).stream().findFirst();
    }

    public DocumentRecord queue(UUID id, UUID owner) {
        return jdbc.queryForObject("""
                UPDATE documents SET status='QUEUED',processing_error=NULL,processed_at=NULL
                WHERE id=? AND owner_id=? RETURNING *
                """, this::mapRow, id, owner);
    }

    public void markProcessing(UUID id) {
        jdbc.update("UPDATE documents SET status='PROCESSING' WHERE id=?", id);
    }

    public void markFailed(UUID id, String code) {
        jdbc.update("""
                UPDATE documents SET status='FAILED',processing_error=?,processed_at=CURRENT_TIMESTAMP,
                chunk_count=0,extracted_characters=0,source_pages=NULL WHERE id=?
                """, code, id);
    }

    public void markExtracted(UUID id, int count, int characters, Integer pages) {
        jdbc.update("""
                UPDATE documents SET status='EXTRACTED',chunk_count=?,extracted_characters=?,
                source_pages=?,processed_at=CURRENT_TIMESTAMP,processing_error=NULL WHERE id=?
                """, count, characters, pages, id);
    }


    public DocumentRecord queueIndex(UUID id, UUID owner) {
        return jdbc.queryForObject("""
                UPDATE documents SET status='EXTRACTED',indexing_error=NULL WHERE id=? AND owner_id=? RETURNING *
                """, this::mapRow,id,owner);
    }

    private DocumentRecord mapRow(ResultSet row, int index) throws SQLException {
        return new DocumentRecord(row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
                row.getString("original_filename"), row.getString("media_type"), row.getString("file_type"),
                row.getLong("size_bytes"), row.getString("sha256"), row.getString("status"),
                row.getTimestamp("created_at").toInstant(), row.getInt("chunk_count"), row.getInt("extracted_characters"),
                row.getObject("source_pages", Integer.class), row.getTimestamp("processed_at") == null ? null :
                row.getTimestamp("processed_at").toInstant(), row.getString("processing_error"), row.getInt("indexed_chunk_count"),
                row.getTimestamp("indexed_at") == null ? null : row.getTimestamp("indexed_at").toInstant(),
                row.getString("indexing_error"));
    }
}
