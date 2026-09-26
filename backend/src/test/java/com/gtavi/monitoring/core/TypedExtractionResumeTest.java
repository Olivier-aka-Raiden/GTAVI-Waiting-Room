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
        var service=new AiExtractionService();
        service.checkpoints=repository; service.maxCalls=1;
        var calls=new AtomicInteger();
        service.rockstarMain=content->{
            calls.incrementAndGet();
            return new RockstarMainData("2026-11-19",List.of("PS5"),true,null,"Official announcement");
        };
        String html="<main>"+"source evidence ".repeat(6000)+"</main>";
        com.fasterxml.jackson.databind.JsonNode result=null;
        for(int i=0;i<10 && result==null;i++) result=service.extractFromHtml(html,"rockstar_main");
        assertNotNull(result);
        assertTrue(calls.get()>1);
        int before=calls.get();
        assertEquals(result,service.extractFromHtml(html,"rockstar_main"));
        assertEquals(before,calls.get());
    }

    @Test void conflictingFactsRestartWithoutKeepingAnIncorrectEarlierChunk() {
        var service = new AiExtractionService();
        service.checkpoints = repository;
        service.maxCalls = 4;
        var inputs = new java.util.ArrayList<String>();
        service.rockstarMain = content -> {
            inputs.add(content);
            return new RockstarMainData(inputs.size() == 1 ? "2026-11-19" : "2026-12-01",
                List.of("PS5"), true, null, "Official announcement");
        };
        String html = "<main>" + "source evidence ".repeat(2500) + "</main>";
        assertNull(service.extractFromHtml(html, "rockstar_main"));
        var recovered = service.extractFromHtml(html, "rockstar_main");
        assertNotNull(recovered);
        assertEquals("2026-12-01", recovered.path("releaseDate").asText());
        assertEquals(inputs.get(0), inputs.get(2), "Retry must revisit the first conflicting chunk");
    }
}
