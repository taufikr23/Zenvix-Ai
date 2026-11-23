package com.documind.chat;

import java.util.regex.Pattern;

public final class ImageSearchIntentDetector {
    private ImageSearchIntentDetector() {}

    private static final Pattern GENERATION_PATTERN = Pattern.compile(
            "(?i)\\b(buatkan|buat|lukiskan|generate|bikinin|gambarin|ciptakan)\\s+(gambar|ilustrasi|lukisan|foto|desain|karya)\\b"
    );

    private static final Pattern IMAGE_REQUEST_PATTERN = Pattern.compile(
            "(?i)\\b(foto|gambar|potret|wallpaper|ilustrasi)\\b"
    );

    private static final Pattern LEADING_PHRASE = Pattern.compile(
            "(?i)^(apakah\\s+kamu\\s+bisa\\s+)?(tolong\\s+)?(tampilkan|tampil|berikan\\s+(saya|kami)?|carikan|cari|minta|lihat|kasih|bagikan|sajikan|kirimkan)\\s+(kan\\s+)?(beberapa\\s+)?(foto|gambar|potret|wallpaper|visual)\\s+(terbaru|asli|dari|tentang|untuk)?\\s*"
    );

    private static final Pattern SIMPLE_PREFIX = Pattern.compile(
            "(?i)^(foto|gambar|potret|wallpaper)\\s+(terbaru|asli|dari|tentang)?\\s*"
    );

    public record Intent(boolean isImageRequest, boolean isAiGeneration, String query) {}

    public static Intent detect(String question) {
        if (question == null || question.isBlank()) {
            return new Intent(false, false, null);
        }
        String text = question.strip();

        // 1. Check AI image generation intent
        if (GENERATION_PATTERN.matcher(text).find()) {
            String prompt = GENERATION_PATTERN.matcher(text).replaceFirst("").replaceAll("^[\\s,.:;!?-]+", "").strip();
            if (!prompt.isBlank()) {
                return new Intent(true, true, prompt);
            }
        }

        // 2. Check general image / photo request intent
        if (IMAGE_REQUEST_PATTERN.matcher(text).find()) {
            // Check if it's asking for photos/pictures
            String cleaned = text.replaceAll("[?.,!]+$", "").strip();
            String query = LEADING_PHRASE.matcher(cleaned).replaceFirst("");
            if (query.equals(cleaned)) {
                query = SIMPLE_PREFIX.matcher(cleaned).replaceFirst("");
            }
            query = query.strip();

            // If there's an actual subject to search
            if (!query.isBlank() && !query.equalsIgnoreCase(text) && query.length() >= 2) {
                return new Intent(true, false, query);
            }

            // Also match "apakah kamu bisa memberikan saya foto Soekarno"
            if (text.toLowerCase().contains("foto ") || text.toLowerCase().contains("gambar ")) {
                var m = Pattern.compile("(?i)(?:foto|gambar)\\s+(?:dari|tentang|terbaru\\s+)?(.+)").matcher(cleaned);
                if (m.find()) {
                    String extracted = m.group(1).strip();
                    if (!extracted.isBlank() && extracted.length() >= 2) {
                        return new Intent(true, false, extracted);
                    }
                }
            }
        }

        return new Intent(false, false, null);
    }
}
