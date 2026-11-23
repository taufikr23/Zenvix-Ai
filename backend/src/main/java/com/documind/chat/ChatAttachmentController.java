package com.documind.chat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations/{conversation}/attachments")
public class ChatAttachmentController {
    private final ChatAttachments attachments;
    public ChatAttachmentController(ChatAttachments attachments){this.attachments=attachments;}
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID conversation,@PathVariable UUID id){
        var file=attachments.download(UUID.fromString(jwt.getSubject()),conversation,id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
}
