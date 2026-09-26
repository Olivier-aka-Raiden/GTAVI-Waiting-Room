package com.gtavi.monitoring.core;

import com.gtavi.config.RedisBackedTest;
import com.gtavi.persistence.RedisPersistence;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ResourceLock("gtavi-test-chat-model")
class MainPageExtractionPipelineTest extends RedisBackedTest {
    @Inject MonitoringOrchestrator orchestrator;
    @Inject RedisPersistence persistence;
    private String html;
    private ExtractorQuteSanityTest.CapturingChatModel model;

    @BeforeEach void replaceRemoteBoundariesOnly() {
        model = new ExtractorQuteSanityTest.CapturingChatModel();
        model.response = """
            {"releaseDate":"2026-11-19","platforms":["PS5","XSX"],
             "preorderAvailable":true,"preorderLabel":"Pre-Order Now","headlineStatus":"Coming November 19, 2026"}
            """;
        QuarkusMock.installMockForType(model, ChatModel.class);
        QuarkusMock.installMockForType(new HttpFetcher() {
            @Override public String fetch(String url) { return html; }
        }, HttpFetcher.class);
    }

    @Test void capturedHomepageCompletesWithinBudgetAndPublishesSuccessfulSnapshot() throws Exception {
        try (var stream = getClass().getResourceAsStream("/news/official-home.html")) {
            html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String input = ExtractionInput.prepare(html, "rockstar_main");
        assertTrue(input.length() < 60000, "Decorative assets must not dominate extraction: " + input.length());
        assertTrue(input.contains("November 19"));
        assertTrue(input.contains("Pre-Order"));
        assertFalse(input.contains("srcset="));
        var run = orchestrator.runCheck(Set.of("ROCKSTAR_MAIN"));
        assertEquals(1, run.successfulSources());
        assertEquals(0, run.failedSources());
        assertEquals(0, run.pendingSources());
        assertTrue(model.calls > 0 && model.calls <= 4);
        assertEquals("2026-11-19", persistence.getLatestSuccessfulSnapshotData("ROCKSTAR_MAIN").path("releaseDate").asText());
    }

    @Test void budgetExhaustionIsPendingAndResumesWithoutAdvancingBaseline() {
        html = "<main>" + "Detailed official GTA VI source information. ".repeat(3500) + "</main>";
        var before = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("releaseDate", "2026-10-01");
        persistence.saveSnapshot("ROCKSTAR_MAIN", "https://www.rockstargames.com/VI/", before, "old", true, null);
        var pending = orchestrator.runCheck(Set.of("ROCKSTAR_MAIN"));
        assertEquals(1, pending.pendingSources());
        assertEquals(0, pending.failedSources());
        assertEquals(0, pending.successfulSources());
        assertEquals(0, pending.eventsCreated());
        assertEquals(before, persistence.getLatestSuccessfulSnapshotData("ROCKSTAR_MAIN"));
        assertEquals(4, model.calls);
        var completed = orchestrator.runCheck(Set.of("ROCKSTAR_MAIN"));
        assertEquals(1, completed.successfulSources());
        assertEquals(0, completed.pendingSources());
        assertEquals("2026-11-19", persistence.getLatestSuccessfulSnapshotData("ROCKSTAR_MAIN").path("releaseDate").asText());
    }
}
