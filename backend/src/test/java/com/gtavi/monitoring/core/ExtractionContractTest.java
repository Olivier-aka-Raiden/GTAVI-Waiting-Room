package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.monitoring.diff.DiffEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void emptyLaterChunkCannotEraseObservedEditionDetails() throws Exception {
        var merged = json.createObjectNode();
        ExtractionMerge.into(merged, json.readTree("""
            {"editions":[{"name":"Standard Edition","platforms":["PS5"],"features":["Bonus"],"preorderAvailable":true}]}
            """));
        ExtractionMerge.into(merged, json.readTree("""
            {"editions":[{"name":"Standard Edition","platforms":[],"features":[],"preorderAvailable":null}]}
            """));
        var edition = merged.path("editions").get(0);
        assertEquals("PS5", edition.path("platforms").get(0).asText());
        assertEquals("Bonus", edition.path("features").get(0).asText());
        assertTrue(edition.path("preorderAvailable").asBoolean());
    }

    @Test void nullPreorderIsNotAnExplicitClosure() throws Exception {
        var events = new DiffEngine().diff("ROCKSTAR_MAIN", "https://www.rockstargames.com/VI/",
            json.readTree("{\"preorderAvailable\":true}"), json.readTree("{\"preorderAvailable\":null}"));
        assertTrue(events.isEmpty());
        assertTrue(new DiffEngine().diff("ROCKSTAR_MAIN", "https://www.rockstargames.com/VI/",
            json.readTree("{\"preorderAvailable\":true}"), json.readTree("{\"preorderAvailable\":false}"))
            .stream().anyMatch(event -> "PREORDER_CLOSED".equals(event.getEventType())));
    }

    @Test void everyPromptEditionTypeSurvivesRetailValidation() {
        var input = json.createObjectNode();
        var products = input.putArray("products");
        for (String type : java.util.List.of("STANDARD","DELUXE","ULTIMATE","COLLECTOR","SPECIAL","BUNDLE","UPGRADE","UNKNOWN"))
            products.addObject().put("name","GTA VI " + type).put("edition",type).put("platform","PS5")
                .put("url","https://store.example/product/" + type);
        var output = new RetailerProductValidator().validate("TEST","https://store.example/",input).path("products");
        assertEquals(products.size(), output.size());
        for (int i = 0; i < output.size(); i++) assertEquals(products.get(i).path("edition"), output.get(i).path("edition"));
    }
}
