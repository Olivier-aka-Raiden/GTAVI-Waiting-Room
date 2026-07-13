package com.gtavi.monitoring.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Uses LangChain4j + LLM to extract structured GTA VI data from raw HTML.
 * This replaces brittle CSS selectors — the AI adapts to any site redesign.
 */
@ApplicationScoped
public class AiExtractionService {

    @Inject
    ExtractionAgent agent;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extract structured GTA VI data from HTML.
     */
    public JsonNode extractFromHtml(String html, String sourceType) {
        String prompt = buildExtractionPrompt(sourceType);
        String truncatedHtml = truncateHtml(html, 40_000);

        try {
            String response = agent.extract(truncatedHtml, prompt);
            return parseAndValidate(response, sourceType);
        } catch (Exception e) {
            Log.errorf("AI extraction failed for %s: %s", sourceType, e.getMessage());
            return null;
        }
    }

    private String buildExtractionPrompt(String sourceType) {
        return switch (sourceType) {
            case "rockstar_main" -> """
                Extract: releaseDate (YYYY-MM-DD), platforms (array), preorderAvailable (bool), preorderLabel, headlineStatus.
                Return ONLY valid JSON with no markdown or explanation.
                """;
            case "rockstar_editions" -> """
                Extract: editions array with name, type (STANDARD/DELUXE/ULTIMATE/COLLECTOR/SPECIAL/BUNDLE/UPGRADE/UNKNOWN),
                description, features array, platforms array, preorderAvailable bool.
                Also: hasCollectorEdition bool.
                Return ONLY valid JSON.
                """;
            case "rockstar_media" -> """
                Extract: videos array with title, mediaType (TRAILER/GAMEPLAY/CHARACTER_CLIP/COVER_ART_ANIMATION/OTHER_VIDEO),
                publicationDate (YYYY-MM-DD), videoUrl, thumbnailUrl.
                Classify by title keywords: "trailer"→TRAILER, "gameplay"→GAMEPLAY, "cover art"→COVER_ART_ANIMATION.
                Return ONLY valid JSON.
                """;
            default -> """
                Extract all GTA VI relevant structured data. Return ONLY valid JSON.
                """;
        };
    }

    private JsonNode parseAndValidate(String response, String sourceType) {
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        try {
            JsonNode node = mapper.readTree(json);
            Log.debugf("AI extraction OK for %s", sourceType);
            return node;
        } catch (JsonProcessingException e) {
            Log.errorf("AI response not valid JSON for %s: %.200s", sourceType, response);
            return null;
        }
    }

    private String truncateHtml(String html, int maxChars) {
        if (html.length() <= maxChars) return html;
        int head = (int) (maxChars * 0.6);
        int tail = (int) (maxChars * 0.3);
        return html.substring(0, head) + "\n<!-- TRUNCATED -->\n" +
               html.substring(html.length() - tail);
    }
}
