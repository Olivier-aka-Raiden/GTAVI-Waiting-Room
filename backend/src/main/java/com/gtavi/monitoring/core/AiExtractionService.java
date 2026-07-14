package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Facade that delegates HTML extraction to the appropriate typed LangChain4j AiService.
 * Each extractor returns a strongly-typed DTO — no manual JSON parsing needed.
 */
@ApplicationScoped
public class AiExtractionService {

    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject RetailerProductsExtractor retailerProducts;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extract structured GTA VI data from HTML.
     * Returns a Jackson JsonNode for backward compatibility with DiffEngine and MonitoringOrchestrator.
     */
    public JsonNode extractFromHtml(String html, String sourceType) {
        String truncatedHtml = truncateHtml(html, 40_000);

        try {
            return switch (sourceType) {
                case "rockstar_main" -> {
                    var data = rockstarMain.extract(truncatedHtml);
                    yield mapper.convertValue(data, JsonNode.class);
                }
                case "rockstar_editions" -> {
                    var data = rockstarEditions.extract(truncatedHtml);
                    yield mapper.convertValue(data, JsonNode.class);
                }
                case "rockstar_media" -> {
                    var data = rockstarMedia.extract(truncatedHtml);
                    yield mapper.convertValue(data, JsonNode.class);
                }
                case "retailer" -> {
                    RetailerProductsData data = retailerProducts.extract(truncatedHtml);
                    yield mapper.convertValue(data, JsonNode.class);
                }
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
     * Extract retailer products as a typed DTO (for use by MonitoringOrchestrator.persistOffers).
     */
    public RetailerProductsData extractRetailerProducts(String html) {
        String truncatedHtml = truncateHtml(html, 40_000);
        return retailerProducts.extract(truncatedHtml);
    }

    private String truncateHtml(String html, int maxChars) {
        if (html.length() <= maxChars) return html;
        int head = (int) (maxChars * 0.6);
        int tail = (int) (maxChars * 0.3);
        return html.substring(0, head) + "\n<!-- TRUNCATED -->\n" +
               html.substring(html.length() - tail);
    }
}
