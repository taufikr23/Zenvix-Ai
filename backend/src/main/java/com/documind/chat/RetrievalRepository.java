package com.documind.chat;
import com.documind.indexing.IndexingProperties;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class RetrievalRepository {
    private final JdbcTemplate jdbc;
    public RetrievalRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Hit> search(UUID owner,UUID document,float[] vector,int topK,double threshold) {
        return jdbc.query("""
                SELECT c.id,c.document_id,c.chunk_index,c.page_number,c.content,d.original_filename,
                       1-(c.embedding <=> ?::vector) AS score
                FROM document_chunks c JOIN documents d ON d.id=c.document_id AND d.owner_id=c.owner_id
                WHERE c.owner_id=? AND c.document_id=? AND d.status='READY'
                  AND c.embedding IS NOT NULL AND c.embedding_model=?
                  AND 1-(c.embedding <=> ?::vector)>=?
                ORDER BY score DESC,c.chunk_index LIMIT ?
                """,(row,index)->new Hit(row.getObject("id",UUID.class),row.getObject("document_id",UUID.class),
                        row.getInt("chunk_index"),row.getObject("page_number",Integer.class),
                        row.getString("original_filename"),row.getString("content"),row.getDouble("score")),
                Arrays.toString(vector),owner,document,IndexingProperties.MODEL,Arrays.toString(vector),threshold,topK);
    }
    public record Hit(UUID chunkId,UUID documentId,int chunkIndex,Integer pageNumber,String filename,String content,double score) {
        @Override public String toString(){return "Hit[chunkId="+chunkId+",score="+score+"]";}
    }
}
