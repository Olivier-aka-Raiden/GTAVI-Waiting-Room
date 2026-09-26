package com.gtavi.monitoring.retailer;

import com.gtavi.monitoring.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors the Rockstar Games official store for GTA VI pre-orders.
 * As of July 2026, Rockstar already offers pre-orders for Standard
 * and Ultimate editions directly on their site. The AI extraction
 * detects edition names, prices, platforms, and purchase URLs.
 */
@ApplicationScoped
public class RockstarStoreMonitor implements GameSourceMonitor {

    private static final String CODE = "ROCKSTAR_STORE";
    private static final String URL = "https://www.rockstargames.com/VI/editions";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Rockstar Games Store"; }
    @Override public boolean isOfficial() { return true; }
    @Override public int checkIntervalSeconds() { return 1800; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            // Preserve links, platform labels and structured data for ExtractionInput.
            String content = html;
            // Use editions extractor — this page is an edition comparison, not a retailer listing
            var extraction = aiExtraction.extractFromHtml(content, "rockstar_editions");
            if (extraction.state() != ExtractionResult.State.COMPLETE) return extraction.toMonitorResult(CODE, URL);
            var editionsData = extraction.data();
            if (editionsData == null || !editionsData.path("editions").isArray() || editionsData.path("editions").isEmpty()) {
                aiExtraction.discardCompletedResult(content, "rockstar_editions");
                return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "Extraction did not contain confirmed editions; retaining existing offers");
            }
            // Convert editions to product format for the retailer pipeline
            var data = editionsToProducts(editionsData);
            if (data.path("products").isEmpty()) {
                aiExtraction.discardCompletedResult(content, "rockstar_editions");
                return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "No usable edition names; retaining existing offers");
            }
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("Rockstar Store monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }

    /**
     * Convert RockstarEditionsData (editions array) to the products format
     * that the retailer pipeline expects. Observed platform labels are canonicalized;
     * absent or unrecognized labels retain one general official edition-page link.
     */
    private JsonNode editionsToProducts(JsonNode editionsData) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var products = mapper.createArrayNode();
        var editions = editionsData.get("editions");
        if (editions == null || !editions.isArray()) {
            var result = mapper.createObjectNode();
            result.set("products", products);
            return result;
        }

        for (JsonNode edition : editions) {
            String name = edition.path("name").asText("").strip();
            if (name.isBlank()) continue;
            String type = edition.has("type") ? edition.get("type").asText() : "UNKNOWN";
            Boolean preorder = edition.hasNonNull("preorderAvailable") ? edition.get("preorderAvailable").asBoolean() : null;

            var platforms = new java.util.LinkedHashSet<String>();
            for (JsonNode platformNode : edition.path("platforms")) {
                String normalized = PlatformNames.normalize(platformNode.asText(null));
                if (normalized != null && !"UNKNOWN".equals(normalized)) platforms.add(normalized);
            }
            if (platforms.isEmpty()) platforms.add("UNKNOWN");
            for (String platform : platforms) {
                var product = mapper.createObjectNode();
                product.put("name", name);
                product.put("edition", type);
                product.put("platform", platform);
                product.put("availability", preorder == null ? "UNKNOWN" : preorder ? "PREORDER" : "UNAVAILABLE");
                product.putNull("price");
                product.putNull("currency");
                product.put("url", URL); // editions page URL — no per-product store link in this format
                products.add(product);
            }
        }

        var result = mapper.createObjectNode();
        result.set("editions", editions);
        result.set("products", products);
        return result;
    }
}
