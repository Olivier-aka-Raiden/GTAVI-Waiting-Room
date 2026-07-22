package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Traces the exact AI response from the Cloud Run logs through
 * editionsToProducts → RetailerProductValidator → persistOffers.
 */
public class RockstarPipelineTraceTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The exact AI response body captured from Cloud Run logs
     * (revision 00040-gwm, 2026-07-19T19:19:20Z)
     */
    private static final String AI_RESPONSE = """
    {
      "editions": [
        {
          "name": "Ultimate Edition",
          "type": "ULTIMATE",
          "description": "An exclusive collection of items threaded across all aspects of Jason and Lucia's story.",
          "features": ["Grand Theft Auto VI", "Vintage Vice City Pack (Pre-order Bonus)"],
          "platforms": [],
          "preorderAvailable": true
        },
        {
          "name": "Standard Edition",
          "type": "STANDARD",
          "description": "Get the standard edition of Grand Theft Auto VI along with pre-order bonuses.",
          "features": ["Grand Theft Auto VI", "Vintage Vice City Pack (Pre-order Bonus)"],
          "platforms": [],
          "preorderAvailable": true
        }
      ],
      "hasCollectorEdition": false
    }""";

    @Test
    void traceFullPipeline() throws Exception {
        JsonNode aiData = mapper.readTree(AI_RESPONSE);

        // Step 1: Verify AI response has editions but NOT products
        assertTrue(aiData.has("editions"), "AI response must have 'editions'");
        assertEquals(2, aiData.get("editions").size(), "Should have 2 editions");
        assertFalse(aiData.has("products"), "AI response should NOT have 'products' yet");

        // Step 2: editionsToProducts conversion (simulates RockstarStoreMonitor logic)
        JsonNode editions = aiData.get("editions");
        var productsNode = mapper.createArrayNode();
        for (JsonNode edition : editions) {
            String name = edition.get("name").asText();
            String type = edition.get("type").asText();
            boolean preorder = edition.get("preorderAvailable").asBoolean();

            for (String platform : new String[]{"PS5", "XSX"}) {
                var product = mapper.createObjectNode();
                product.put("name", name);
                product.put("edition", type);
                product.put("platform", platform);
                product.put("availability", preorder ? "PREORDER" : "UNKNOWN");
                product.putNull("price");
                product.putNull("currency");
                product.put("url", "https://www.rockstargames.com/VI/editions");
                productsNode.add(product);
            }
        }
        var productsData = mapper.createObjectNode();
        productsData.set("products", productsNode);

        assertEquals(4, productsNode.size(), "Should generate 4 products (2 editions × 2 platforms)");

        // Step 3: Run through RetailerProductValidator
        RetailerProductValidator validator = new RetailerProductValidator();
        JsonNode validated = validator.validate("ROCKSTAR_STORE",
            "https://www.rockstargames.com/VI/editions", productsData);

        JsonNode acceptedProducts = validated.get("products");
        assertNotNull(acceptedProducts, "Validated data must have 'products'");
        assertEquals(4, acceptedProducts.size(),
            "All 4 products should pass validation. Got: " + acceptedProducts.size());

        // Print details of accepted products
        for (JsonNode p : acceptedProducts) {
            System.out.printf("  ACCEPTED: %s | edition=%s | platform=%s | avail=%s | url=%s%n",
                p.get("name").asText(),
                p.get("edition").asText(),
                p.get("platform").asText(),
                p.get("availability").asText(),
                p.get("url").asText());
        }

        // Step 4: Verify each product would match an edition ID
        for (JsonNode p : acceptedProducts) {
            String edition = p.get("edition").asText().toLowerCase();
            String name = p.get("name").asText().toLowerCase();

            // Simulate matchEdition logic
            boolean editionMatch = edition.contains("standard") || edition.contains("ultimate")
                || edition.contains("collector") || edition.contains("deluxe");
            boolean nameMatch = name.contains("standard") || name.contains("ultimate")
                || name.contains("collector") || name.contains("deluxe");

            assertTrue(editionMatch || nameMatch,
                "Product '" + p.get("name").asText() + "' must match an edition: edition="
                + p.get("edition").asText());
        }

        // Step 5: isGtaViGameProduct check
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Ultimate Edition"),
            "'Ultimate Edition' should be recognized as a game product");
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Standard Edition"),
            "'Standard Edition' should be recognized as a game product");
        assertFalse(RetailerProductValidator.isGtaViGameProduct("Grand Theft Auto V"),
            "'Grand Theft Auto V' should be rejected");
    }

    @Test
    void verifyEditionTypesAreRecognized() {
        // The editionsToProducts passes the raw AI type through
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Ultimate Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Standard Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Collector's Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Deluxe Edition"));
    }

    @Test
    void verifyNonGameProductsAreRejected() {
        assertFalse(RetailerProductValidator.isGtaViGameProduct("GTA V Premium Edition"),
            "GTA V should be rejected");
        assertFalse(RetailerProductValidator.isGtaViGameProduct("Soundtrack"),
            "Soundtrack should be rejected");
        assertFalse(RetailerProductValidator.isGtaViGameProduct(""),
            "Empty should be rejected");
    }
}
