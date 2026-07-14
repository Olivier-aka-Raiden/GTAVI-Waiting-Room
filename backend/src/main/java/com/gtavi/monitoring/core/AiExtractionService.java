package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Facade that delegates HTML extraction to the appropriate typed LangChain4j AiService.
 * Each extractor returns a strongly-typed DTO — no manual JSON parsing needed.
 *
 * Before sending HTML to the LLM, we strip noise (scripts, styles, comments)
 * to maximise the signal that fits in the token budget.
 */
@ApplicationScoped
public class AiExtractionService {

    private static final int MAX_CHARS = 60_000; // bumped from 40K — small cost increase, better retailer coverage

    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject RetailerProductsExtractor retailerProducts;
    @Inject YoutubeRssExtractor youtubeRss;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extract structured GTA VI data from HTML.
     * Strips noise (scripts, styles) before sending to the LLM for maximum signal density.
     */
    public JsonNode extractFromHtml(String html, String sourceType) {
        String clean = prepareHtml(html);

        try {
            return switch (sourceType) {
                case "rockstar_main" -> mapper.convertValue(rockstarMain.extract(clean), JsonNode.class);
                case "rockstar_editions" -> mapper.convertValue(rockstarEditions.extract(clean), JsonNode.class);
                case "rockstar_media" -> mapper.convertValue(rockstarMedia.extract(clean), JsonNode.class);
                case "retailer" -> mapper.convertValue(retailerProducts.extract(clean), JsonNode.class);
                case "youtube_rss" -> mapper.convertValue(youtubeRss.extract(clean), JsonNode.class);
                default -> {
                    Log.warnf("Unknown source type: %s — no typed extractor available", sourceType);
                    yield null;
                }
            };
        } catch (Exception e) {
            Log.errorf(e, "AI extraction failed for %s", sourceType);
            return null;
        }
    }

    /**
     * Extract retailer products as a typed DTO (for MonitoringOrchestrator.persistOffers).
     */
    public RetailerProductsData extractRetailerProducts(String html) {
        return retailerProducts.extract(prepareHtml(html));
    }

    // ── HTML preparation ─────────────────────────────────────────────────

    /**
     * Strip noise (scripts, styles, comments, excessive whitespace) then truncate
     * if still over MAX_CHARS. Retailer pages are 150K+ chars of JS/CSS bloat;
     * stripping lets the actual product content survive truncation.
     */
    private String prepareHtml(String html) {
        String stripped = stripNoise(html);
        return truncate(stripped, MAX_CHARS);
    }

    /**
     * Remove elements the LLM doesn't need:
     * <script>...</script>, <style>...</style>, <noscript>...</noscript>,
     * <svg>...</svg>, HTML comments <!-- ... -->, and collapse whitespace.
     */
    static String stripNoise(String html) {
        // Remove whole elements (greedy across lines)
        String cleaned = html
            .replaceAll("(?s)<script[^>]*>.*?</script>", "")
            .replaceAll("(?s)<style[^>]*>.*?</style>", "")
            .replaceAll("(?s)<noscript[^>]*>.*?</noscript>", "")
            .replaceAll("(?s)<svg[^>]*>.*?</svg>", "")
            .replaceAll("(?s)<path[^>]*>.*?</path>", "")
            // HTML comments
            .replaceAll("<!--.*?-->", "")
            // data-* attributes (bloat)
            .replaceAll("\\sdata-[a-zA-Z0-9-]+=(?:\"[^\"]*\"|'[^']*')", "")
            // Collapse whitespace (blank lines / runs of spaces)
            .replaceAll("\\n\\s*\\n", "\n")
            .replaceAll("[ \\t]{2,}", " ");

        return cleaned;
    }

    private String truncate(String html, int maxChars) {
        if (html.length() <= maxChars) return html;
        // Keeps 80% from start, 20% from end (was 60/30 — product content is usually in first 80%)
        int head = (int) (maxChars * 0.8);
        int tail = (int) (maxChars * 0.2);
        return html.substring(0, head) + "\n<!-- SNIP -->\n" +
               html.substring(html.length() - tail);
    }
}
