package com.gtavi.monitoring.core;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises generated AI services, prompt rendering and DTO parsing without an LLM connection. */
@QuarkusTest
@ResourceLock("gtavi-test-chat-model")
class ExtractorQuteSanityTest {

    private static final String HTML = "<html><body>GTA VI fixture</body></html>";

    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject YoutubeRssExtractor youtubeRss;
    @Inject RetailerProductsExtractor retailerProducts;


    @Inject com.gtavi.news.AnnouncementExtractor announcements;

    @Test void independentExtractionCallsNeverCarryPriorMessages() {
        model.response = "{}";
        java.util.List<java.util.function.Consumer<String>> extractors = java.util.List.of(
            text -> rockstarMain.extract(text),
            text -> rockstarEditions.extract(text),
            text -> rockstarMedia.extract(text),
            text -> retailerProducts.extract(text),
            text -> youtubeRss.extract(text),
            text -> announcements.extract("https://www.rockstargames.com/VI/", "GTA VI", text)
        );
        for (var extractor : extractors) {
            extractor.accept("UNIQUE_OLD_OBSERVATION");
            extractor.accept("UNIQUE_CURRENT_OBSERVATION");
            assertEquals(1, model.request.messages().stream().filter(UserMessage.class::isInstance).count());
            assertEquals(0, model.request.messages().stream().filter(AiMessage.class::isInstance).count());
            String request = model.request.messages().toString();
            assertTrue(request.contains("UNIQUE_CURRENT_OBSERVATION"));
            assertFalse(request.contains("UNIQUE_OLD_OBSERVATION"));
        }
    }

    private CapturingChatModel model;

    @BeforeEach
    void replaceOnlyTheRemoteModel() {
        model = new CapturingChatModel();
        QuarkusMock.installMockForType(model, ChatModel.class);
    }

    @Test
    void rockstarMainSystemMessageRendersWithoutQuteError() {
        model.response = """
            {"releaseDate":"2026-11-19","platforms":["PS5"],"preorderAvailable":true}
            """;
        var result = rockstarMain.extract(HTML);
        assertEquals("2026-11-19", result.releaseDate());
        assertTrue(result.preorderAvailable());
        assertRenderedPrompt(HTML);
    }

    @Test
    void rockstarEditionsSystemMessageRendersWithoutQuteError() {
        model.response = """
            {"editions":[{"name":"Standard Edition","type":"STANDARD",
            "platforms":["PS5"],"preorderAvailable":true}],"hasCollectorEdition":false}
            """;
        var result = rockstarEditions.extract(HTML);
        assertEquals("Standard Edition", result.editions().getFirst().name());
        assertFalse(result.hasCollectorEdition());
        assertRenderedPrompt(HTML);
    }

    @Test
    void rockstarMediaSystemMessageRendersWithoutQuteError() {
        model.response = videoResponse();
        var result = rockstarMedia.extract(HTML);
        assertEquals("TRAILER", result.videos().getFirst().mediaType());
        assertRenderedPrompt(HTML);
    }

    @Test
    void youtubeRssSystemMessageRendersWithoutQuteError() {
        String xml = "<rss><title>GTA VI fixture</title></rss>";
        model.response = videoResponse();
        var result = youtubeRss.extract(xml);
        assertEquals("GTA VI Trailer 2", result.videos().getFirst().title());
        assertRenderedPrompt(xml);
    }

    @Test
    void retailerProductsSystemMessageRendersWithoutQuteError() {
        model.response = """
            {"products":[{"name":"GTA VI Standard Edition","edition":"STANDARD",
            "price":79.9,"currency":"CHF","availability":"PREORDER",
            "url":"https://example.test/gta-vi","platform":"PS5"}]}
            """;
        var result = retailerProducts.extract(HTML);
        assertEquals("GTA VI Standard Edition", result.products().getFirst().name());
        assertEquals(79.9, result.products().getFirst().price());
        assertRenderedPrompt(HTML);
    }

    private void assertRenderedPrompt(String input) {
        assertEquals(1, model.calls, "A rendered prompt must reach the model exactly once");
        assertNotNull(model.request);
        assertTrue(model.request.messages().stream()
            .filter(SystemMessage.class::isInstance)
            .map(SystemMessage.class::cast)
            .anyMatch(message -> !message.text().isBlank()));
        var user = model.request.messages().stream()
            .filter(UserMessage.class::isInstance)
            .map(UserMessage.class::cast)
            .findFirst().orElseThrow();
        assertTrue(user.singleText().contains(input), "The user template must interpolate its input");
        assertFalse(user.singleText().contains("{{html}}"));
        assertFalse(user.singleText().contains("{{xml}}"));
    }

    private static String videoResponse() {
        return """
            {"videos":[{"title":"GTA VI Trailer 2","mediaType":"TRAILER",
            "publicationDate":"2025-05-06","videoUrl":"https://example.test/trailer"}]}
            """;
    }

    static final class CapturingChatModel implements ChatModel {
        String response;
        ChatRequest request;
        int calls;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            this.request = request;
            calls++;
            assertNotNull(response, "Each test must supply its model response");
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(response))
                .tokenUsage(new TokenUsage(1, 1))
                .finishReason(FinishReason.STOP)
                .build();
        }
    }
}
