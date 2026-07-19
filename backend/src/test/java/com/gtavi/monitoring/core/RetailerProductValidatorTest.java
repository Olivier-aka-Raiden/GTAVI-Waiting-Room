package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetailerProductValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RetailerProductValidator validator = new RetailerProductValidator();

    @Test
    void rejectsMusicAndUnrelatedSearchResults() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[
              {"name":"Grand Theft Auto VI (Slowed) [Explicit]","url":"/music/123"},
              {"name":"Grand Theft Auto V Premium Edition","url":"/game/456"},
              {"name":"Grand Theft Auto VI Standard Edition","edition":"STANDARD",
               "platform":"PS5","availability":"PREORDER","url":"/en-ch/product/789"}
            ]}
            """);

        var result = validator.validate("PS_STORE",
            "https://store.playstation.com/en-ch/search/gta-vi", extracted);

        assertEquals(1, result.get("products").size());
        assertEquals("Grand Theft Auto VI Standard Edition",
            result.get("products").get(0).get("name").asText());
    }

    @Test
    void resolvesRelativeUrlsAndNormalizesEnums() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[{"name":"GTA 6 Collector's Edition","edition":"collector",
            "platform":"invalid","availability":"in_stock","currency":"eur",
            "price":199.99,"url":"/dp/B123"}]}
            """);

        var product = validator.validate("AMAZON_FR",
            "https://www.amazon.fr/s?k=gta+vi", extracted).get("products").get(0);

        assertEquals("https://www.amazon.fr/dp/B123", product.get("url").asText());
        assertEquals("COLLECTOR", product.get("edition").asText());
        assertEquals("UNKNOWN", product.get("platform").asText());
        assertEquals("IN_STOCK", product.get("availability").asText());
        assertEquals("EUR", product.get("currency").asText());
        assertTrue(product.hasNonNull("canonicalKey"));
    }

    @Test
    void rejectsUnsafeOrHostlessUrls() throws Exception {
        var extracted = mapper.readTree("""
            {"products":[{"name":"Grand Theft Auto VI Standard Edition",
            "url":"javascript:alert(1)"}]}
            """);

        var result = validator.validate("TEST", "https://example.com/search", extracted);

        assertTrue(result.get("products").isEmpty());
    }
}
