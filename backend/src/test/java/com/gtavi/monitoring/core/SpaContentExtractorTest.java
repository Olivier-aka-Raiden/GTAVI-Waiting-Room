package com.gtavi.monitoring.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class SpaContentExtractorTest {

    @Test
    void shouldExtractVisibleContentFromRockstarEditionsPage() throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://www.rockstargames.com/VI/editions"))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Editions page should be accessible");

        String content = SpaContentExtractor.extractContent(response.body());

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

        System.out.println("=== Extracted content (first 1500 chars) ===");
        System.out.println(content.substring(0, Math.min(1500, content.length())));
        System.out.println("=== Total length: " + content.length() + " chars ===");
    }

    @Test
    void shouldReturnOriginalHtmlForNonSpaPage() {
        String plainHtml = "<html><body><h1>Hello World</h1><p>Some content</p></body></html>";
        String result = SpaContentExtractor.extractContent(plainHtml);
        assertEquals(plainHtml, result,
            "Non-SPA HTML should be returned unchanged");
    }

    @Test
    void shouldHandleEmptyAndNullInput() {
        String empty = SpaContentExtractor.extractContent("");
        assertNotNull(empty);

        String content = SpaContentExtractor.extractContent("<html></html>");
        assertNotNull(content);
        // No RSC payloads found — should return original
        assertEquals("<html></html>", content);
    }
}
