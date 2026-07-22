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
    void acceptsGtaViSoundtrackIfUserWantsIt() throws Exception {
        // User explicitly said: accept everything mentioning GTA VI, even soundtracks.
        // Platform requirement still applies though — a soundtrack with PS5 platform passes.
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"Grand Theft Auto VI Official Soundtrack","url":"/music/ost","platform":"PS5"}
            ]}
            """);

        var result = validator.validate("TEST", "https://example.com/gta-vi", extracted);

        assertEquals(1, result.get("products").size());
        assertEquals("Grand Theft Auto VI Official Soundtrack",
            result.get("products").get(0).get("name").asText());
    }
}
