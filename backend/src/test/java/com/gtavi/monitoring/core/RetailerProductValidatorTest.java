package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetailerProductValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RetailerProductValidator validator = new RetailerProductValidator();

    @Test
    void rejectsMusicSlowedAndGtaVProductsButAcceptsGameListings() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"Grand Theft Auto VI (Slowed) [Explicit]","url":"/music/123"},
              {"name":"Grand Theft Auto V Premium Edition","url":"/game/456","platform":"PS5"},
              {"name":"Grand Theft Auto VI Standard Edition","edition":"STANDARD",
               "platform":"PS5","availability":"PREORDER","url":"/en-ch/product/789"}
            ]}
            """);

        var result = validator.validate("PS_STORE",
            "https://store.playstation.com/en-ch/search/gta-vi", extracted);

        // Only the GTA VI Standard Edition with PS5 platform should pass.
        // "Slowed" track rejected: no platform → can't be a game listing.
        // GTA V rejected: isGtaVOnly check catches the old game.
        assertEquals(1, result.get("products").size());
        assertEquals("Grand Theft Auto VI Standard Edition",
            result.get("products").get(0).get("name").asText());
    }

    @Test
    void resolvesRelativeUrlsAndNormalizesEnums() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[{"name":"GTA 6 Collector's Edition","edition":"collector",
            "platform":"ps5","availability":"in_stock","currency":"eur",
            "price":199.99,"url":"/dp/B123"}]}
            """);

        var product = validator.validate("AMAZON_FR",
            "https://www.amazon.fr/s?k=gta+vi", extracted).get("products").get(0);

        assertEquals("https://www.amazon.fr/dp/B123", product.get("url").asText());
        assertEquals("COLLECTOR", product.get("edition").asText());
        assertEquals("PS5", product.get("platform").asText());
        assertEquals("IN_STOCK", product.get("availability").asText());
        assertEquals("EUR", product.get("currency").asText());
        assertTrue(product.hasNonNull("canonicalKey"));
    }

    @Test
    void rejectsUnsafeOrHostlessUrls() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[{"name":"Grand Theft Auto VI Standard Edition",
            "url":"javascript:alert(1)","platform":"PS5"}]}
            """);

        var result = validator.validate("TEST", "https://example.com/search", extracted);

        assertTrue(result.get("products").isEmpty());
    }

    @Test
    void rejectsProductsWithoutRecognizablePlatform() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"Grand Theft Auto VI","url":"/game/123"},
              {"name":"Grand Theft Auto VI","url":"/game/456","platform":"PS5"}
            ]}
            """);

        var result = validator.validate("TEST", "https://example.com/gta-vi", extracted);

        // First product has no platform → rejected. Second has PS5 → accepted.
        assertEquals(1, result.get("products").size());
        assertEquals("PS5", result.get("products").get(0).get("platform").asText());
    }

    @Test
    void infersPlatformFromNameWhenAiOmitsIt() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"GTA VI PS5 Edition","url":"/game/ps5"},
              {"name":"Grand Theft Auto VI - Xbox Series X","url":"/game/xbox"},
              {"name":"GTA 6 PC Download","url":"/game/pc"}
            ]}
            """);

        var result = validator.validate("TEST", "https://example.com/gta-vi", extracted);

        assertEquals(3, result.get("products").size());
        assertEquals("PS5", result.get("products").get(0).get("platform").asText());
        assertEquals("XSX", result.get("products").get(1).get("platform").asText());
        assertEquals("PC", result.get("products").get(2).get("platform").asText());
    }

    @Test
    void rejectsSoundtrackEvenWhenTheModelAssignsAGamePlatform() throws Exception {
        // Music belongs to the news/music pipeline, not a game edition retailer offer.
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"Grand Theft Auto VI Official Soundtrack","url":"/music/ost","platform":"PS5"}
            ]}
            """);

        var result = validator.validate("TEST", "https://example.com/gta-vi", extracted);

        assertTrue(result.get("products").isEmpty());
    }

    @Test
    void acceptsFullPlatformNamesFromStagedOrCachedRockstarProducts() throws Exception {
        var data = mapper.readTree("""
            {"products":[
              {"name":"Ultimate Edition","platform":"PlayStation 5","url":"/VI/editions"},
              {"name":"Ultimate Edition","platform":"Xbox Series X|S","url":"/VI/editions"},
              {"name":"Standard Edition","edition":"STANDARD","platform":"PlayStation 5","url":"/VI/editions"},
              {"name":"Standard Edition","edition":"STANDARD","platform":"Xbox Series X / S","url":"/VI/editions"}
            ]}
            """);
        var products = validator.validate("ROCKSTAR_STORE", "https://www.rockstargames.com/VI/editions", data).path("products");
        assertEquals(4, products.size());
        assertEquals("PS5", products.get(0).path("platform").asText());
        assertEquals("XSX", products.get(1).path("platform").asText());
    }

    @Test
    void rejectsUnusableRockstarObservationsInsteadOfReportingAnEmptyStore() throws Exception {
        for (String data : java.util.List.of("{\"products\":[]}",
                "{\"products\":[{\"name\":\"Standard Edition\",\"platform\":\"future\",\"url\":\"/VI/editions\"}]}")) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate("ROCKSTAR_STORE",
                "https://www.rockstargames.com/VI/editions", mapper.readTree(data)));
        }
    }
}
