package com.documind.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ImageSearchService {
    private static final Logger log = LoggerFactory.getLogger(ImageSearchService.class);
    private static final String USER_AGENT = "ZenvixBot/1.0 (https://zenvix.ai; contact@zenvix.ai)";
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ImageGenerationService generationService;

    public ImageSearchService(ObjectMapper mapper, ImageGenerationService generationService) {
        this.mapper = mapper;
        this.generationService = generationService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<ChatDtos.ImageItem> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String cleanQuery = cleanSearchQuery(query);
        log.info("Searching images for: '{}' (cleaned: '{}')", query, cleanQuery);

        // 1. Try search with cleaned query
        List<ChatDtos.ImageItem> results = executeSearch(cleanQuery);

        // 2. If no results and query contains modifier words, try stripping modifier words
        if (results.isEmpty()) {
            String simplified = stripModifiers(cleanQuery);
            if (!simplified.equalsIgnoreCase(cleanQuery) && !simplified.isBlank()) {
                log.info("Retrying image search with simplified query: '{}'", simplified);
                results = executeSearch(simplified);
            }
        }

        // 3. If still no results and multiple words, try first 1-2 words (core subject)
        if (results.isEmpty() && cleanQuery.contains(" ")) {
            String[] parts = cleanQuery.split("\\s+");
            if (parts.length >= 2) {
                String core = parts[0] + " " + parts[1];
                log.info("Retrying image search with core subject: '{}'", core);
                results = executeSearch(core);
            }
        }

        // 4. Guarantee: If no open web images found, automatically generate photorealistic AI visual!
        if (results.isEmpty() && generationService != null) {
            log.info("No open web images found for '{}', generating photorealistic AI fallback visual", cleanQuery);
            results = generationService.generate("High quality detailed photorealistic visual of " + cleanQuery);
        }

        // Limit results to top 6 images
        if (results.size() > 6) {
            return results.subList(0, 6);
        }
        return results;
    }

    private List<ChatDtos.ImageItem> executeSearch(String term) {
        List<ChatDtos.ImageItem> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var fIdWiki = CompletableFuture.supplyAsync(() -> searchWikipedia(term, "id"), executor);
            var fEnWiki = CompletableFuture.supplyAsync(() -> searchWikipedia(term, "en"), executor);
            var fCommons = CompletableFuture.supplyAsync(() -> searchWikimediaCommons(term), executor);
            var fOpenverse = CompletableFuture.supplyAsync(() -> searchOpenverse(term), executor);

            CompletableFuture.allOf(fIdWiki, fEnWiki, fCommons, fOpenverse)
                    .orTimeout(5, TimeUnit.SECONDS)
                    .exceptionally(ex -> null)
                    .join();

            // Order: Indonesian Wikipedia -> Wikimedia Commons -> English Wikipedia -> Openverse
            addUnique(results, seenUrls, fIdWiki.getNow(List.of()));
            addUnique(results, seenUrls, fCommons.getNow(List.of()));
            addUnique(results, seenUrls, fEnWiki.getNow(List.of()));
            addUnique(results, seenUrls, fOpenverse.getNow(List.of()));
        } catch (Exception e) {
            log.warn("Image search execution error for '{}': {}", term, e.getMessage());
        } finally {
            executor.close();
        }
        return results;
    }

    private void addUnique(List<ChatDtos.ImageItem> target, Set<String> seen, List<ChatDtos.ImageItem> candidates) {
        for (var item : candidates) {
            if (item == null || item.url() == null || item.url().isBlank()) continue;
            String key = normalizeUrl(item.url());
            if (seen.add(key)) {
                target.add(item);
            }
        }
    }

    private String normalizeUrl(String url) {
        return url.toLowerCase().replaceAll("[?#].*$", "");
    }

    private String cleanSearchQuery(String raw) {
        String s = raw.strip();
        // Remove common Indonesian and English query preambles
        s = s.replaceAll("(?i)^(foto|gambar|potret|wallpaper|ilustrasi|tampilkan foto|tampilkan gambar|carikan foto|carikan gambar|berikan saya foto|berikan saya gambar|berikan foto|berikan gambar|minta foto|minta gambar|lihat foto|lihat gambar|show photo|show picture|picture of|photo of)\\s+(tentang|dari|untuk|terbaru|asli)?\\s*", "");
        s = s.replaceAll("(?i)\\s+(terbaru|asli)$", "");
        return s.strip().isBlank() ? raw.strip() : s.strip();
    }

    private String stripModifiers(String query) {
        // Remove extraneous modifiers that cause exact-match search engines to miss
        return query.replaceAll("(?i)\\b(aktris|aktor|actress|actor|logo|interface|tampilan|antarmuka|official|resmi|tokoh|pemeran|film|presiden|president|indonesia)\\b", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private List<ChatDtos.ImageItem> searchWikipedia(String query, String lang) {
        List<ChatDtos.ImageItem> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // Note: Use %7C instead of literal | to prevent java.net.URI IllegalArgumentException
            String url = String.format(
                    "https://%s.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=%s&gsrlimit=6&prop=pageimages%%7Cinfo&piprop=thumbnail%%7Coriginal&pithumbsize=600&inprop=url&format=json",
                    lang, encoded
            );
            var req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return list;

            JsonNode root = mapper.readTree(res.body());
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isObject()) return list;

            var pageList = new ArrayList<JsonNode>();
            pages.fields().forEachRemaining(entry -> pageList.add(entry.getValue()));
            pageList.sort(Comparator.comparingInt(p -> p.path("index").asInt(99)));

            for (JsonNode page : pageList) {
                JsonNode thumbnail = page.path("thumbnail");
                JsonNode original = page.path("original");
                String imgUrl = original.hasNonNull("source") ? original.get("source").asText() :
                        (thumbnail.hasNonNull("source") ? thumbnail.get("source").asText() : null);
                if (imgUrl == null || !isValidImageUrl(imgUrl)) continue;

                String thumbUrl = thumbnail.hasNonNull("source") ? thumbnail.get("source").asText() : imgUrl;
                String pageTitle = page.path("title").asText("");
                String fullUrl = page.path("fullurl").asText("https://" + lang + ".wikipedia.org");
                int width = original.path("width").asInt(thumbnail.path("width").asInt(0));
                int height = original.path("height").asInt(thumbnail.path("height").asInt(0));

                list.add(new ChatDtos.ImageItem(
                        pageTitle,
                        imgUrl,
                        thumbUrl,
                        fullUrl,
                        lang.equals("id") ? "Wikipedia Indonesia" : "Wikipedia",
                        "Wikipedia Contributors",
                        width > 0 ? width : null,
                        height > 0 ? height : null
                ));
            }
        } catch (Exception e) {
            log.warn("Wikipedia ({}) search error for '{}': {}", lang, query, e.getMessage());
        }
        return list;
    }

    private List<ChatDtos.ImageItem> searchWikimediaCommons(String query) {
        List<ChatDtos.ImageItem> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // Note: Use %7C instead of literal | to prevent java.net.URI IllegalArgumentException
            String url = String.format(
                    "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=%s&gsrnamespace=6&gsrlimit=8&prop=imageinfo&iiprop=url%%7Csize%%7Cmime%%7Cextmetadata&iiurlwidth=600&format=json",
                    encoded
            );
            var req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return list;

            JsonNode root = mapper.readTree(res.body());
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isObject()) return list;

            var pageList = new ArrayList<JsonNode>();
            pages.fields().forEachRemaining(entry -> pageList.add(entry.getValue()));
            pageList.sort(Comparator.comparingInt(p -> p.path("index").asInt(99)));

            for (JsonNode page : pageList) {
                JsonNode infoArray = page.path("imageinfo");
                if (!infoArray.isArray() || infoArray.isEmpty()) continue;
                JsonNode info = infoArray.get(0);

                String mime = info.path("mime").asText("");
                if (!mime.startsWith("image/") || mime.contains("djvu")) continue;

                String imgUrl = info.path("url").asText("");
                String thumbUrl = info.hasNonNull("thumburl") ? info.path("thumburl").asText() : imgUrl;
                if (!isValidImageUrl(imgUrl)) continue;

                // For SVGs, use rasterized PNG thumburl as the display url
                if (mime.contains("svg") && !thumbUrl.isBlank()) {
                    imgUrl = thumbUrl;
                }

                String title = page.path("title").asText("").replaceFirst("^File:", "").replaceAll("\\.[a-zA-Z0-9]+$", "");
                String descriptionUrl = info.path("descriptionurl").asText("https://commons.wikimedia.org");
                int width = info.path("width").asInt(0);
                int height = info.path("height").asInt(0);

                String author = "Wikimedia Commons";
                JsonNode extMeta = info.path("extmetadata");
                if (extMeta.has("Artist")) {
                    String rawArtist = extMeta.path("Artist").path("value").asText("");
                    if (!rawArtist.isBlank()) {
                        author = rawArtist.replaceAll("<[^>]*>", "").strip();
                    }
                }

                list.add(new ChatDtos.ImageItem(
                        title,
                        imgUrl,
                        thumbUrl,
                        descriptionUrl,
                        "Wikimedia Commons",
                        author,
                        width > 0 ? width : null,
                        height > 0 ? height : null
                ));
            }
        } catch (Exception e) {
            log.warn("Wikimedia Commons search error for '{}': {}", query, e.getMessage());
        }
        return list;
    }

    private List<ChatDtos.ImageItem> searchOpenverse(String query) {
        List<ChatDtos.ImageItem> list = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("https://api.openverse.org/v1/images/?q=%s&page_size=6", encoded);
            var req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return list;

            JsonNode root = mapper.readTree(res.body());
            JsonNode results = root.path("results");
            if (!results.isArray()) return list;

            for (JsonNode item : results) {
                String imgUrl = item.path("url").asText("");
                if (!isValidImageUrl(imgUrl)) continue;

                String thumbUrl = item.hasNonNull("thumbnail") ? item.path("thumbnail").asText() : imgUrl;
                String title = item.path("title").asText("Gambar");
                String sourceUrl = item.hasNonNull("foreign_landing_url") ? item.path("foreign_landing_url").asText() : imgUrl;
                String provider = item.path("provider").asText("Openverse");
                String creator = item.path("creator").asText(provider);
                int width = item.path("width").asInt(0);
                int height = item.path("height").asInt(0);

                list.add(new ChatDtos.ImageItem(
                        title,
                        imgUrl,
                        thumbUrl,
                        sourceUrl,
                        provider.substring(0, 1).toUpperCase() + provider.substring(1),
                        creator,
                        width > 0 ? width : null,
                        height > 0 ? height : null
                ));
            }
        } catch (Exception e) {
            log.warn("Openverse search error for '{}': {}", query, e.getMessage());
        }
        return list;
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase().replaceAll("[?#].*$", "");
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false;
        // Avoid audio, video, or binary documents
        if (lower.endsWith(".ogg") || lower.endsWith(".oga") || lower.endsWith(".ogv") ||
            lower.endsWith(".mp3") || lower.endsWith(".mp4") || lower.endsWith(".webm") ||
            lower.endsWith(".pdf") || lower.endsWith(".djvu")) {
            return false;
        }
        return true;
    }
}
