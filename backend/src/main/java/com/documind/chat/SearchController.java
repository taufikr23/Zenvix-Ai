package com.documind.chat;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/documents/{id}/search")
public class SearchController {
    private final RetrievalService service;
    public SearchController(RetrievalService service){this.service=service;}
    @PostMapping
    public ResponseEntity<List<RetrievalRepository.Hit>> search(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
            @Valid @RequestBody ChatDtos.Search request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(UUID.fromString(jwt.getSubject()),id,request.query()));
    }
}
