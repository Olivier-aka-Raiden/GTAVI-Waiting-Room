package com.gtavi.monitoring.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SpaContentExtractorTest {

    @Test
    void shouldExtractVisibleContentFromRockstarEditionsPage() throws Exception {
        String html;
        try (var fixture = getClass().getResourceAsStream("/fixtures/rockstar-editions-rsc.html")) {
            assertNotNull(fixture, "The offline RSC fixture must be on the test classpath");
            html = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }
        String content = SpaContentExtractor.extractContent(html);
        assertNotEquals(html, content, "The RSC payloads must actually be extracted");

        assertNotNull(content, "Content should not be null");
        assertFalse(content.isBlank(), "Content should not be blank");

        // Edition names
        assertTrue(content.contains("Ultimate Edition"),
            "Should contain Ultimate Edition, got:\n" + content.substring(0, Math.min(500, content.length())));
        assertTrue(content.contains("Standard Edition"),
            "Should contain Standard Edition");

        // Pre-order indicators
        assertTrue(content.contains("Pre-Order"),
            "Should contain Pre-Order text");

        // Platform references
        assertTrue(content.contains("PlayStation"),
            "Should mention PlayStation");
        assertTrue(content.toLowerCase().contains("xbox"),
            "Should mention Xbox");

        // Store URLs for the AI to use
        assertTrue(content.contains("playstation.com"),
            "Should contain PlayStation store URL");

        // Game reference
        assertTrue(content.contains("Grand Theft Auto"),
            "Should contain game title");

        assertFalse(content.contains("<script"));
        assertFalse(content.contains("/VI/_next/"));
        assertFalse(content.contains("css-card-token"));
        assertFalse(content.contains("tracking.png"));
    }

    @Test
    void shouldReturnOriginalHtmlForNonSpaPage() {
        String plainHtml = "<html><body><h1>Hello World</h1><p>Some content</p></body></html>";
        String result = SpaContentExtractor.extractContent(plainHtml);
        assertEquals(plainHtml, result,
            "Non-SPA HTML should be returned unchanged");
    }

    @Test
    void shouldHandleEmptyHtml() {
        String empty = SpaContentExtractor.extractContent("");
        assertNotNull(empty);

        String content = SpaContentExtractor.extractContent("<html></html>");
        assertNotNull(content);
        // No RSC payloads found — should return original
        assertEquals("<html></html>", content);
    }
}
