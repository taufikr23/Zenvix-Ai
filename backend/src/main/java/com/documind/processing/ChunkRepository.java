package com.documind.processing;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {
    private final JdbcTemplate jdbc;
    public ChunkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void replace(UUID document, UUID owner, List<TextChunker.Chunk> chunks) {
        jdbc.update("DELETE FROM document_chunks WHERE document_id=? AND owner_id=?", document, owner);
        jdbc.batchUpdate("""
                INSERT INTO document_chunks(id,document_id,owner_id,chunk_index,source_section,page_number,content,character_count)
                VALUES (?,?,?,?,?,?,?,?)
                """, chunks, 100, (statement, chunk) -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, document);
            statement.setObject(3, owner);
            statement.setInt(4, chunk.index());
            statement.setInt(5, chunk.sourceSection());
            statement.setObject(6, chunk.pageNumber(), java.sql.Types.INTEGER);
            statement.setString(7, chunk.content());
            statement.setInt(8, chunk.characterCount());
        });
    }

    public List<ChunkResponse> list(UUID document, UUID owner, int page, int size) {
        return jdbc.query("""
                SELECT c.* FROM document_chunks c JOIN documents d ON d.id=c.document_id AND d.owner_id=c.owner_id
                WHERE c.document_id=? AND c.owner_id=? AND d.status IN ('EXTRACTED','READY','INDEX_FAILED')
                ORDER BY c.chunk_index LIMIT ? OFFSET ?
                """, (row, index) -> new ChunkResponse(row.getObject("id", UUID.class),
                        row.getInt("chunk_index"), row.getInt("source_section"),
                        row.getObject("page_number", Integer.class), row.getString("content"),
                        row.getInt("character_count")), document, owner, size, (long) page * size);
    }

    public record ChunkResponse(UUID id, int index, int sourceSection, Integer pageNumber,
                                String content, int characterCount) {
        @Override public String toString() { return "ChunkResponse[id=" + id + "]"; }
    }
    public record ChunkPage(List<ChunkResponse> items, int page, int size, int totalElements) {}
}
