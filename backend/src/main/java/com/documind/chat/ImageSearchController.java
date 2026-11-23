package com.documind.chat;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/images")
public class ImageSearchController {
    private final ImageSearchService searchService;
    private final ImageGenerationService generationService;
    private final HttpClient proxyClient;

    public ImageSearchController(ImageSearchService searchService, ImageGenerationService generationService) {
        this.searchService = searchService;
        this.generationService = generationService;
        this.proxyClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ChatDtos.ImageItem>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String q
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(searchService.search(q));
    }

    @GetMapping("/generate")
    public ResponseEntity<List<ChatDtos.ImageItem>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String prompt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(generationService.generate(prompt));
    }

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String url
    ) {
        if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ZenvixBot/1.0 (image proxy)")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            var res = proxyClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                return ResponseEntity.status(res.statusCode()).build();
            }
            String contentType = res.headers().firstValue("Content-Type").orElse("image/jpeg");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                    .body(res.body());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
