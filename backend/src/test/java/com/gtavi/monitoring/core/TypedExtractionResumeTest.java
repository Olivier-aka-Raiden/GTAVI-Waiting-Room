package com.gtavi.monitoring.core;

import com.gtavi.config.RedisBackedTest;
import com.gtavi.news.NewsRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TypedExtractionResumeTest extends RedisBackedTest {
    @Inject NewsRepository repository;

    @Test void legacyExtractorResumesLargePagesAndCachesSuccessfulResult() {
        var service = service(1);
        var calls = new AtomicInteger();
        service.rockstarMain = content -> {
            calls.incrementAndGet();
            return facts("2026-11-19");
        };
        String html = "<main>" + "source evidence ".repeat(6000) + "</main>";
        ExtractionResult result = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.PENDING, result.state());
        assertNull(result.data(), "Partial extraction must not replace the public baseline");
        assertTrue(result.processedCharacters() > 0);
        for (int i = 0; i < 10 && result.state() == ExtractionResult.State.PENDING; i++)
            result = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.COMPLETE, result.state());
        assertTrue(calls.get() > 1);
        int before = calls.get();
        assertEquals(result, service.extractFromHtml(html, "rockstar_main"));
        assertEquals(before, calls.get());
    }

    @Test void conflictingFactsRestartWithoutKeepingAnIncorrectEarlierChunk() {
        var service = service(4);
        var inputs = new java.util.ArrayList<String>();
        service.rockstarMain = content -> {
            inputs.add(content);
            return facts(inputs.size() == 1 ? "2026-11-19" : "2026-12-01");
        };
        String html = "<main>" + "source evidence ".repeat(2500) + "</main>";
        var conflict = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.FAILED, conflict.state());
        assertTrue(conflict.reason().contains("Conflicting"));
        var recovered = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.COMPLETE, recovered.state());
        assertEquals("2026-12-01", recovered.data().path("releaseDate").asText());
        assertEquals(inputs.get(0), inputs.get(2));
    }

    @Test void modelFailureRetainsProgressAndRetriesOnlyTheUnfinishedChunk() {
        var service = service(4);
        var inputs = new java.util.ArrayList<String>();
        service.rockstarMain = content -> {
            inputs.add(content);
            if (inputs.size() == 2) throw new IllegalStateException("Simulated model outage");
            return facts("2026-11-19");
        };
        String html = "<main>" + "source evidence ".repeat(2500) + "</main>";
        var failed = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.FAILED, failed.state());
        assertTrue(failed.processedCharacters() > 0);
        var recovered = service.extractFromHtml(html, "rockstar_main");
        assertEquals(ExtractionResult.State.COMPLETE, recovered.state());
        assertEquals(inputs.get(1), inputs.get(2));
        assertNotEquals(inputs.get(0), inputs.get(2));
    }

    @Test void blankSourceIsAnActualFailureNotPendingWork() {
        var result = service(1).extractFromHtml("<html><body></body></html>", "rockstar_main");
        assertEquals(ExtractionResult.State.FAILED, result.state());
        assertNull(result.data());
    }

    private AiExtractionService service(int limit) {
        var service = new AiExtractionService();
        service.checkpoints = repository;
        service.maxCalls = limit;
        return service;
    }

    private RockstarMainData facts(String date) {
        return new RockstarMainData(date, List.of("PS5"), true, null, "Official announcement");
    }
}
