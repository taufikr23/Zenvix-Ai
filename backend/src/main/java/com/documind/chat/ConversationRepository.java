package com.documind.chat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class ConversationRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public ConversationRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public long count(UUID owner){return jdbc.queryForObject("SELECT count(*) FROM conversations WHERE owner_id=?",Long.class,owner);}
    public ChatDtos.Conversation create(UUID owner,UUID document){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO conversations(id,owner_id,document_id) VALUES (?,?,?)",id,owner,document);
        return find(owner,id,false).orElseThrow();
    }
    public Optional<ChatDtos.Conversation> find(UUID owner,UUID id,boolean lock){
        return jdbc.query("""
                SELECT c.*,d.original_filename FROM conversations c LEFT JOIN documents d ON d.id=c.document_id AND d.owner_id=c.owner_id
                WHERE c.id=? AND c.owner_id=?
                """+(lock?" FOR UPDATE OF c":""),this::conversation,id,owner).stream().findFirst();
    }
    public List<ChatDtos.Conversation> list(UUID owner,int page,int size){
        return jdbc.query("""
                SELECT c.*,d.original_filename FROM conversations c LEFT JOIN documents d ON d.id=c.document_id AND d.owner_id=c.owner_id
                WHERE c.owner_id=? ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?
                """,this::conversation,owner,size,(long)page*size);
    }
    public long countTurns(UUID owner,UUID conversation){
        return jdbc.queryForObject("SELECT count(*) FROM chat_turns WHERE owner_id=? AND conversation_id=?",Long.class,owner,conversation);
    }
    public Optional<ChatDtos.Turn> existing(UUID owner,UUID conversation,UUID request){
        return jdbc.query("SELECT * FROM chat_turns WHERE owner_id=? AND conversation_id=? AND request_id=?",
                this::turn,owner,conversation,request).stream().findFirst();
    }
    public List<ChatDtos.Turn> turns(UUID owner,UUID conversation,int page,int size){
        return jdbc.query("SELECT * FROM chat_turns WHERE owner_id=? AND conversation_id=? ORDER BY turn_index LIMIT ? OFFSET ?",
                this::turn,owner,conversation,size,(long)page*size);
    }
    public List<ChatDtos.Turn> recentTurns(UUID owner,UUID conversation){
        var recent=jdbc.query("SELECT * FROM chat_turns WHERE owner_id=? AND conversation_id=? ORDER BY turn_index DESC LIMIT 12",
                this::turn,owner,conversation);
        var history=new ArrayList<ChatDtos.Turn>();
        int characters=0;
        for(var turn:recent){
            int length=turn.question().length()+turn.answer().length();
            if(characters+length>ChatLimits.CONTEXT_CHARACTERS)break;
            history.add(turn);characters+=length;
        }
        Collections.reverse(history);
        return List.copyOf(history);
    }
    public List<String> recentQuestions(UUID owner,UUID conversation){
        var result=jdbc.query("SELECT question FROM chat_turns WHERE owner_id=? AND conversation_id=? ORDER BY turn_index DESC LIMIT 2",
                (row,index)->{String q=row.getString("question");return q.substring(0,Math.min(q.length(),250));},owner,conversation);
        Collections.reverse(result);return result;
    }
    public ChatDtos.Turn save(UUID owner,UUID conversation,UUID request,int index,String question,ChatDtos.Answer answer){
        if(index==0) {
            String title=question.replaceAll("\\s+"," ").strip();
            title=title.substring(0,title.offsetByCodePoints(0,Math.min(80,title.codePointCount(0,title.length()))));
            jdbc.update("UPDATE conversations SET title=? WHERE id=? AND owner_id=?",title,conversation,owner);
        }
        String sources;
        String images;
        try{
            sources=mapper.writeValueAsString(answer.sources());
            images=mapper.writeValueAsString(answer.images()==null?List.of():answer.images());
        }catch(Exception failure){throw new IllegalStateException("Sumber tidak dapat disimpan.");}
        return jdbc.queryForObject("""
                INSERT INTO chat_turns(id,conversation_id,owner_id,request_id,turn_index,question,answer,found,sources,images)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb) RETURNING *
                """,this::turn,UUID.randomUUID(),conversation,owner,request,index,question,answer.text(),answer.found(),sources,images);
    }
    public boolean delete(UUID owner,UUID conversation){return jdbc.update("DELETE FROM conversations WHERE id=? AND owner_id=?",conversation,owner)==1;}
    private ChatDtos.Conversation conversation(ResultSet row,int index)throws SQLException{
        return new ChatDtos.Conversation(row.getObject("id",UUID.class),row.getObject("document_id",UUID.class),
                row.getString("original_filename"),row.getString("title"),row.getTimestamp("created_at").toInstant());
    }
    private ChatDtos.Turn turn(ResultSet row,int index)throws SQLException{
        try{
            var sources=mapper.readValue(row.getString("sources"),new TypeReference<List<ChatDtos.Source>>(){});
            List<ChatDtos.ImageItem> images=List.of();
            try {
                String imagesJson=row.getString("images");
                if(imagesJson!=null&&!imagesJson.isBlank()){
                    images=mapper.readValue(imagesJson,new TypeReference<List<ChatDtos.ImageItem>>(){});
                }
            }catch(SQLException ignored){}
            return new ChatDtos.Turn(row.getObject("id",UUID.class),row.getObject("request_id",UUID.class),row.getInt("turn_index"),
                row.getString("question"),row.getString("answer"),row.getBoolean("found"),
                sources,
                row.getTimestamp("created_at").toInstant(),
                List.of(),
                images);
        }catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new SQLException("Sumber riwayat tidak valid.");}
    }
}
