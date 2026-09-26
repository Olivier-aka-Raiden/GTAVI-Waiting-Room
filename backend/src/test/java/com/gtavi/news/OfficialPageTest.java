package com.gtavi.news;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficialPageTest {
    @Test void extractsMetadataWithoutPretendingTheShellIsAFullArticle() {
        var page = OfficialPage.parse("https://www.rockstargames.com/newswire/article/abc/title",
            "<html><head><meta property='og:title' content='GTA VI: The Album'>"
            + "<meta property='og:image' content='/album.jpg'>"
            + "<meta property='og:description' content='Official soundtrack announcement'></head><body></body></html>");
        assertEquals("GTA VI: The Album", page.title());
        assertEquals("https://www.rockstargames.com/album.jpg", page.imageUrl());
        assertFalse(page.complete());
        assertTrue(page.content().contains("Official soundtrack"));
    }
    @Test void keepsMiddleContentAndStructuredPurchaseEvidence() {
        String url = "https://store.rockstargames.com/merchandise/box";
        var page = OfficialPage.parse(url, "<html><body><main><h1>GTA VI Vice City Collection</h1>"
            + "<p>" + "Decorative text. ".repeat(10000) + "</p>"
            + "<p>A limited Collector’s Box. Game sold separately.</p>"
            + "<a href='/buy/box'>Pre-order</a></main>"
            + "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":\"GTA VI Vice City Collection\","
            + "\"offers\":{\"price\":\"399.99\",\"priceCurrency\":\"USD\"}}</script></body></html>");
        assertTrue(page.content().contains("Game sold separately"));
        assertTrue(page.links().contains("https://store.rockstargames.com/buy/box"));
        assertTrue(page.structured().contains("399.99"));
        assertTrue(page.chunks(12000).stream().anyMatch(c -> c.contains("Game sold separately")));
    }
    @Test void canonicalizesArticleAliasesAndRejectsUnsafeFetches() {
        assertEquals(OfficialPage.identity("https://www.rockstargames.com/newswire/article/abc/title?utm_source=x"),
            OfficialPage.identity("https://www.rockstargames.com/newswire/article/abc"));
        assertFalse(OfficialPage.allowed("http://127.0.0.1/private"));
        assertFalse(OfficialPage.allowed("https://www.rockstargames.com.evil.test/VI"));
        assertFalse(OfficialPage.allowed("https://user:pass@www.rockstargames.com/VI"));
        assertNull(OfficialPage.resolve("https://www.rockstargames.com", "javascript:alert(1)"));
    }
}
