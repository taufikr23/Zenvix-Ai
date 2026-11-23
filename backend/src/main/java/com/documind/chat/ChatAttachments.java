package com.documind.chat;

import com.documind.common.ApiException;
import com.documind.document.FileValidator;
import com.documind.processing.DocumentExtractor;
import com.documind.processing.ExtractionFailure;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ChatAttachments {
    public static final long MAX_FILE=5L*1024*1024, MAX_TOTAL=10L*1024*1024;
    private final JdbcTemplate jdbc;
    private final FileValidator validator;
    private final DocumentExtractor extractor;
    public ChatAttachments(JdbcTemplate jdbc,FileValidator validator,DocumentExtractor extractor) {
        this.jdbc=jdbc;this.validator=validator;this.extractor=extractor;
    }
    public record Content(UUID id,String filename,String mediaType,byte[] bytes,String text,String hash,List<AttachmentVisuals.Visual> visuals) {
        public Content(UUID id,String filename,String mediaType,byte[] bytes,String text,String hash){
            this(id,filename,mediaType,bytes,text,hash,AttachmentVisuals.read(mediaType,bytes));
        }
        public long mediaBytes(){return visuals.stream().mapToLong(visual->visual.bytes().length).sum();}
        public ChatDtos.Attachment descriptor(){return new ChatDtos.Attachment(id,filename,mediaType,bytes.length);}
        @Override public String toString(){return "Attachment[id="+id+"]";}
    }
    public List<Content> prepare(List<MultipartFile> files) {
        if(files.size()>3)throw invalid("Maksimal 3 lampiran per pesan.");
        if(files.stream().mapToLong(MultipartFile::getSize).sum()>MAX_TOTAL)throw invalid("Total lampiran maksimal 10 MB.");
        var result=new ArrayList<Content>();
        int characters=0;long mediaBytes=0;
        for(var file:files){
            if(file.isEmpty()||file.getSize()>MAX_FILE)throw invalid("Setiap lampiran harus berisi data dan maksimal 5 MB.");
            String name=file.getOriginalFilename();
            if(name==null||name.isBlank()||name.length()>255||name.contains("/")||name.contains("\\")||name.contains(":")
                    ||name.codePoints().anyMatch(Character::isISOControl))throw invalid("Nama lampiran tidak valid.");
            String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
            byte[] bytes;String type;String text="";
            try {
                if(Set.of("png","jpg","jpeg").contains(ext)){
                    try(var input=file.getInputStream()){bytes=input.readNBytes((int)MAX_FILE+1);}
                    if(bytes.length==0||bytes.length>MAX_FILE)throw invalid("Ukuran gambar tidak valid.");
                    type=validateImage(bytes,ext);
                }else{
                    var validated=validator.validate(file);
                    bytes=validated.bytes();type=validated.mediaType();
                    text=extractor.extract(validated.fileType(),bytes).sections().stream()
                            .map(section->section.text()).collect(java.util.stream.Collectors.joining("\n"));
                }
                characters+=text.length();
                if(characters>ChatLimits.ATTACHMENT_CHARACTERS)throw invalid("Total teks lampiran maksimal 500.000 karakter per pesan. Bagi dokumen yang lebih panjang menjadi beberapa bagian.");
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                var content=new Content(UUID.randomUUID(),name.strip(),type,bytes,text,hash);
                if(text.isBlank()&&content.visuals().isEmpty())throw invalid("Dokumen tidak berisi teks atau gambar yang dapat dibaca.");
                mediaBytes+=content.mediaBytes();
                if(mediaBytes>MAX_TOTAL)throw invalid("Total gambar dan PDF yang diproses maksimal 10 MB per pesan.");
                result.add(content);
            }catch(ExtractionFailure failure){throw invalid("Teks file tidak dapat dibaca atau terlalu besar. Gunakan PDF berisi teks, DOCX, atau TXT.");}
            catch(ApiException failure){throw failure;}
            catch(Exception failure){throw invalid("Lampiran rusak atau tidak dapat dibaca.");}
        }
        return List.copyOf(result);
    }
    private String validateImage(byte[] bytes,String extension)throws IOException {
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw invalid("Isi gambar tidak valid.");
            var reader=readers.next();
            try{
                reader.setInput(input);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                String expected=extension.equals("png")?"png":"jpeg";
                if(!format.equals(expected))throw invalid("Isi gambar tidak sesuai ekstensi PNG atau JPG.");
                if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000)throw invalid("Resolusi gambar maksimal 20 megapiksel.");
                if(reader.read(0)==null)throw invalid("Gambar tidak dapat dibaca.");
                return "image/"+format;
            }finally{reader.dispose();}
        }
    }
    public List<ChatDtos.Attachment> list(UUID owner,UUID turn){
        return jdbc.query("SELECT id,filename,media_type,octet_length(content) AS size FROM chat_attachments WHERE owner_id=? AND turn_id=? ORDER BY position",
                (r,i)->new ChatDtos.Attachment(r.getObject("id",UUID.class),r.getString("filename"),r.getString("media_type"),r.getLong("size")),owner,turn);
    }
    public ChatDtos.Turn decorate(UUID owner,ChatDtos.Turn turn){
        return new ChatDtos.Turn(turn.id(),turn.requestId(),turn.index(),turn.question(),turn.answer(),turn.found(),turn.sources(),turn.createdAt(),list(owner,turn.id()),turn.images());
    }
    public void save(UUID owner,UUID turn,List<Content> files){
        for(int i=0;i<files.size();i++){
            var file=files.get(i);
            jdbc.update("INSERT INTO chat_attachments(id,turn_id,owner_id,position,filename,media_type,content,extracted_text,sha256) VALUES (?,?,?,?,?,?,?,?,?)",
                    file.id(),turn,owner,i,file.filename(),file.mediaType(),file.bytes(),file.text(),file.hash());
        }
    }
    public void checkCapacity(UUID owner,List<Content> files){
        if(files.isEmpty())return;
        long used=jdbc.queryForObject("SELECT COALESCE(sum(octet_length(content)),0) FROM chat_attachments WHERE owner_id=?",Long.class,owner);
        if(used+files.stream().mapToLong(f->f.bytes().length).sum()>100L*1024*1024)
            throw invalid("Penyimpanan lampiran mencapai 100 MB. Hapus percakapan lama untuk mengosongkan ruang.");
    }
    public boolean matches(UUID owner,UUID turn,List<Content> files){
        var stored=jdbc.query("SELECT filename,sha256 FROM chat_attachments WHERE owner_id=? AND turn_id=? ORDER BY position",
                (r,i)->List.of(r.getString("filename"),r.getString("sha256")),owner,turn);
        return stored.equals(files.stream().map(f->List.of(f.filename(),f.hash())).toList());
    }
    public List<Content> load(UUID owner,UUID turn){
        return jdbc.query("SELECT * FROM chat_attachments WHERE owner_id=? AND turn_id=? ORDER BY position",this::content,owner,turn);
    }
    public Content download(UUID owner,UUID conversation,UUID attachment){
        return jdbc.query("SELECT a.* FROM chat_attachments a JOIN chat_turns t ON t.id=a.turn_id WHERE a.owner_id=? AND t.conversation_id=? AND a.id=?",
                this::content,owner,conversation,attachment).stream().findFirst().orElseThrow(ChatErrors::missing);
    }
    private Content content(java.sql.ResultSet r,int i)throws java.sql.SQLException {
        return new Content(r.getObject("id",UUID.class),r.getString("filename"),r.getString("media_type"),r.getBytes("content"),r.getString("extracted_text"),r.getString("sha256"));
    }
    private ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT",message);}
}
