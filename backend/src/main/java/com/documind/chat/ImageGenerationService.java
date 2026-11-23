package com.documind.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ImageGenerationService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

    public List<ChatDtos.ImageItem> generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        try {
            log.info("Generating AI image for prompt: '{}'", prompt);
            String encoded = URLEncoder.encode(prompt.strip(), StandardCharsets.UTF_8);
            String imageUrl = "https://image.pollinations.ai/prompt/" + encoded + "?width=1024&height=1024&nologo=true";
            String thumbUrl = "https://image.pollinations.ai/prompt/" + encoded + "?width=512&height=512&nologo=true";

            return List.of(new ChatDtos.ImageItem(
                    prompt.length() > 60 ? prompt.substring(0, 60) + "…" : prompt,
                    imageUrl,
                    thumbUrl,
                    imageUrl,
                    "AI Generated",
                    "Zenvix AI Visualizer",
                    1024,
                    1024
            ));
        } catch (Exception e) {
            log.warn("Image generation failed: {}", e.getMessage());
            return List.of();
        }
    }
}
