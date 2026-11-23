package com.documind.chat;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service){this.service=service;}
    @PostMapping
    public ResponseEntity<ChatDtos.Conversation> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ChatDtos.CreateConversation request){
        var result=service.create(owner(jwt),request.documentId());
        return ResponseEntity.created(URI.create("/api/conversations/"+result.id())).cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping
    public ResponseEntity<ChatDtos.ConversationPage> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(owner(jwt),page,size));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ChatDtos.Conversation> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(owner(jwt),id));
    }
    @GetMapping("/{id}/messages")
    public ResponseEntity<ChatDtos.TurnPage> messages(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.messages(owner(jwt),id,page,size));
    }
    @PostMapping(value="/{id}/messages",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatDtos.Turn> ask(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ChatDtos.Ask request){
        var result=service.ask(owner(jwt),id,request);
        return ResponseEntity.status(result.created()?201:200).cacheControl(CacheControl.noStore()).body(result.turn());
    }
    @PostMapping(value="/{id}/messages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatDtos.Turn> askWithAttachments(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
            @Valid @RequestPart("message") ChatDtos.Ask request,
            @RequestPart(value="files",required=false) java.util.List<org.springframework.web.multipart.MultipartFile> files){
        var result=service.ask(owner(jwt),id,request,files==null?java.util.List.of():files);
        return ResponseEntity.status(result.created()?201:200).cacheControl(CacheControl.noStore()).body(result.turn());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
        service.delete(owner(jwt),id);return ResponseEntity.noContent().build();
    }
    private UUID owner(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
