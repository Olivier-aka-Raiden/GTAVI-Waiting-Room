package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductEvidenceTest {
    @Test void acceptsExplicitLocaleFormattedPricesWithoutGuessingCurrency() {
        assertTrue(ProductEvidence.price("GTA VI Album costs 72,90 CHF", 72.9, "CHF"));
        assertTrue(ProductEvidence.price("GTA VI box EUR 1.249,99", 1249.99, "EUR"));
        assertTrue(ProductEvidence.price("GTA VI box USD 1,249.99", 1249.99, "USD"));
        assertTrue(ProductEvidence.price("GTA VI box CHF 1'249.99", 1249.99, "CHF"));
        assertFalse(ProductEvidence.price("GTA VI box costs $72.90", 72.9, "USD"));
        assertFalse(ProductEvidence.price("GTA VI box costs 119.99 USD", 19.99, "USD"));
        assertFalse(ProductEvidence.price("GTA VI box costs 72.90 ZZZ", 72.9, "ZZZ"));
    }

    @Test void anotherProductsPriceElsewhereInTheChunkDoesNotVerifyThisProduct() throws Exception {
        var json = new ObjectMapper();
        var analyzer = new NewsAnalyzer();
        analyzer.json = json; analyzer.structured = new StructuredProducts();
        String excerpt = "GTA VI Vinyl announced today with no price.";
        var extraction = json.readValue("""
            {"relevant":true,"category":"MUSIC","importance":"MAJOR","evidence":"GTA VI Vinyl announced today with no price.",
             "products":[{"name":"GTA VI Vinyl","category":"VINYL","price":40,"currency":"EUR",
                          "evidence":"GTA VI Vinyl announced today with no price."}],"facts":[]}
            """, AnnouncementExtraction.class);
        analyzer.ai = (url,title,chunk) -> extraction;
        var page = OfficialPage.parse("https://www.rockstargames.com/VI/music",
            "<h1>GTA VI music</h1><main>" + excerpt + " Another product costs 40 EUR.</main>");
        var product = analyzer.analyze(page).path("products").get(0);
        assertNotNull(product);
        assertTrue(product.path("price").isNull());
        assertTrue(product.path("currency").isNull());
    }

    @Test void validProductEvidencePreservesCommaDecimalPriceAndUnknownFlags() throws Exception {
        var json = new ObjectMapper();
        var analyzer = new NewsAnalyzer();
        analyzer.json = json; analyzer.structured = new StructuredProducts();
        String excerpt = "GTA VI Vinyl costs 72,90 CHF. Pre-order now.";
        var extraction = json.readValue("""
            {"relevant":true,"category":"MUSIC","importance":"MAJOR","evidence":"GTA VI Vinyl costs 72,90 CHF. Pre-order now.",
             "products":[{"name":"GTA VI Vinyl","category":"VINYL","price":72.9,"currency":"CHF","availability":"PREORDER",
                          "limited":null,"gameIncluded":null,"evidence":"GTA VI Vinyl costs 72,90 CHF. Pre-order now."}],"facts":[]}
            """, AnnouncementExtraction.class);
        analyzer.ai = (url,title,chunk) -> extraction;
        var page = OfficialPage.parse("https://www.rockstargames.com/VI/music", "<h1>GTA VI music</h1><main>" + excerpt + "</main>");
        var product = analyzer.analyze(page).path("products").get(0);
        assertEquals(72.9, product.path("price").asDouble());
        assertTrue(product.path("limited").isNull());
        assertTrue(product.path("gameIncluded").isNull());
    }

    @Test void explicitAlternativeWordingDoesNotRequireAnEnglishKeywordAllowlist() throws Exception {
        var json = new ObjectMapper();
        var analyzer = new NewsAnalyzer();
        analyzer.json = json; analyzer.structured = new StructuredProducts();
        String excerpt = "GTA VI Box: only 500 copies will be made. You must purchase the game separately.";
        var extraction = json.readValue("""
            {"relevant":true,"category":"COLLECTIBLE","importance":"MAJOR",
             "evidence":"GTA VI Box: only 500 copies will be made. You must purchase the game separately.",
             "products":[{"name":"GTA VI Box","category":"COLLECTIBLE","limited":true,"gameIncluded":false,
              "evidence":"GTA VI Box: only 500 copies will be made. You must purchase the game separately."}],"facts":[]}
            """, AnnouncementExtraction.class);
        analyzer.ai = (url,title,chunk) -> extraction;
        var page = OfficialPage.parse("https://www.rockstargames.com/VI/box", "<h1>GTA VI Box</h1><main>" + excerpt + "</main>");
        var product = analyzer.analyze(page).path("products").get(0);
        assertTrue(product.path("limited").asBoolean());
        assertFalse(product.path("gameIncluded").asBoolean());
        assertFalse(product.path("gameIncluded").isNull());
    }
}
