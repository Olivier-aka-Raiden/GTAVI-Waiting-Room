package com.gtavi.news;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void productionAlbumObservationsBecomeOneCardWithDistinctFormats() throws Exception {
        var raw = json.readTree(getClass().getResourceAsStream("/fixtures/incident-products-2026-09-26.json"));
        var rows = new ArrayList<JsonNode>();
        raw.path("items").forEach(rows::add);
        var products = ProductCatalog.group(rows);
        var albums = products.stream().filter(p -> "ALBUM".equals(p.path("category").asText())).toList();
        assertEquals(1, albums.size());
        assertEquals(3, albums.getFirst().path("offers").size());
        assertFalse(albums.getFirst().has("limited"));
        assertTrue(albums.getFirst().path("offers").toString().contains("limitededitionvinyl"));
        assertEquals(1, products.stream().filter(p -> "COLLECTIBLE".equals(p.path("category").asText())).count());
        assertEquals(products, ProductCatalog.group(rows), "Grouping must be stable across refreshes");
    }
    @Test void separateSkuVariantsAndCurrenciesAreNotCollapsed() throws Exception {
        ObjectNode first = (ObjectNode)json.readTree("""
            {"id":"a","name":"GTA VI Collector Box","category":"COLLECTIBLE","purchaseUrl":"https://store.rockstargames.com/products/box",
             "offers":[{"purchaseUrl":"https://store.rockstargames.com/products/box","variant":"red","price":40,"currency":"EUR"}]}
            """);
        ObjectNode second = first.deepCopy();
        second.put("id","b");
        ((ObjectNode)second.path("offers").get(0)).put("variant","blue").put("currency","CHF");
        assertEquals(2, ProductCatalog.group(List.of(first, second)).size());
    }
}
