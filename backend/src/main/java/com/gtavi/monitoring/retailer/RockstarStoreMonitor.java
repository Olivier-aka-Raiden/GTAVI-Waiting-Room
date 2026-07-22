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
            // Extract visible content from Next.js SPA — raw HTML is just script tags
            String content = SpaContentExtractor.extractContent(html);
            // Use editions extractor — this page is an edition comparison, not a retailer listing
            var editionsData = aiExtraction.extractFromHtml(content, "rockstar_editions");
            if (editionsData == null || !editionsData.has("editions")) {
                return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "AI extraction null");
            }
            // Convert editions to product format for the retailer pipeline
            var data = editionsToProducts(editionsData);
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("Rockstar Store monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }

    /**
     * Convert RockstarEditionsData (editions array) to the products format
     * that the retailer pipeline expects. Each edition gets a PS5 and XSX variant.
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
            String name = edition.has("name") ? edition.get("name").asText() : null;
            String type = edition.has("type") ? edition.get("type").asText() : "UNKNOWN";
            boolean preorder = edition.has("preorderAvailable") && edition.get("preorderAvailable").asBoolean();

            // Create a product entry for each platform Rockstar links to
            for (String platform : new String[]{"PS5", "XSX"}) {
                var product = mapper.createObjectNode();
                product.put("name", name != null ? name : type);
                product.put("edition", type);
                product.put("platform", platform);
                product.put("availability", preorder ? "PREORDER" : "UNKNOWN");
                product.putNull("price");
                product.putNull("currency");
                product.put("url", URL); // editions page URL — no per-product store link in this format
                products.add(product);
            }
        }

        var result = mapper.createObjectNode();
        result.set("products", products);
        return result;
    }
}
