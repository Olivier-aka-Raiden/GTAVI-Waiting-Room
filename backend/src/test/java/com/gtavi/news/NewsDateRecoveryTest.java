package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.config.RedisBackedTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class NewsDateRecoveryTest extends RedisBackedTest {
    @Inject NewsRepository repository;
    @Inject ObjectMapper json;
    static final String COLLECTION = "https://www.rockstargames.com/newswire/article/9k2a49ook82o57";
    static final String ALBUM = "https://www.rockstargames.com/newswire/article/7599a881942544";

    @Test void discoveryRepairsExistingDatesWithoutReplayingNotifications() throws Exception {
        for (String url : new String[] { COLLECTION, ALBUM }) repository.upsert("articles",
            json.createObjectNode().put("id", OfficialPage.identity(url)).put("sourceUrl", url).put("title", "Keep title"));
        var discovery = new NewswireDiscovery();
        discovery.repository = repository; discovery.json = json; discovery.gtaTag = 666;
        var listing = json.createObjectNode();
        var results = listing.putArray("results");
        results.addObject().put("url", COLLECTION).put("created", "9/24/26, 11:00 AM");
        results.addObject().put("url", ALBUM).put("created", "9/17/26, 8:00 AM");
        discovery.ingest(listing, true);
        assertEquals("2026-09-24", repository.read("articles:" + OfficialPage.identity(COLLECTION)).path("publishedAt").asText());
        assertEquals("Keep title", repository.read("articles:" + OfficialPage.identity(COLLECTION)).path("title").asText());
        assertNull(repository.read("pending:" + OfficialPage.identity(COLLECTION)));
        discovery.ingest(listing, true);
        assertEquals(OfficialPage.identity(COLLECTION), repository.list("articles", 0, 1).getFirst().path("id").asText());
        assertEquals(OfficialPage.identity(ALBUM), repository.list("articles", 1, 1).getFirst().path("id").asText());
    }
    @Test void completeCachedExtractionAcceptsNewPageDateWithoutAnotherAiCall() {
        var page = new OfficialPage(COLLECTION, "Collection", "Description", "", "2026-09-24", "Unchanged content", "", java.util.List.of(), true);
        String id = OfficialPage.identity(COLLECTION);
        String hash = OfficialPage.fingerprint(NewsAnalyzer.VERSION + "\n" + page.content() + "\n" + page.structured() + "\n" + page.links());
        var cache = json.createObjectNode().put("hash", hash);
        cache.putObject("article").put("id", id).put("relevant", true).put("enrichmentPending", false).put("publishedAt", "");
        repository.cache("cache:" + id, cache);
        var monitor = new OfficialNewsMonitor();
        monitor.repository = repository; monitor.json = json;
        monitor.analyzer = new NewsAnalyzer() {
            @Override public ObjectNode analyze(OfficialPage p, ExtractionBudget budget, ObjectNode prior) {
                throw new AssertionError("Completed content must not be re-extracted just to recover a date");
            }
        };
        assertEquals("2026-09-24", monitor.extract(page, id, new ExtractionBudget(0, 0)).path("publishedAt").asText());
        assertEquals("2026-09-24", repository.read("cache:" + id).path("article").path("publishedAt").asText());
        repository.write("listing:" + id, json.createObjectNode().put("publishedAt", "2026-09-24"));
        var withoutDate = new OfficialPage(page.url(), page.title(), page.description(), page.imageUrl(), "", page.content(), page.structured(), page.links(), true);
        assertEquals("2026-09-24", monitor.extract(withoutDate, id, new ExtractionBudget(0, 0)).path("publishedAt").asText());
    }
    @Test void datedAnnouncementsLeadAndRepresentTheirLinkedUndatedLandingPages() {
        String landing = "https://www.rockstargames.com/VI/music";
        var album = json.createObjectNode().put("id", "album").put("category", "MUSIC").put("sourceUrl", ALBUM).put("publishedAt", "2026-09-17");
        album.putArray("relationshipLinks").add(landing);
        repository.upsert("articles", album);
        repository.upsert("articles", json.createObjectNode().put("id", "collection").put("sourceUrl", COLLECTION).put("category", "COLLECTIBLE").put("publishedAt", "2026-09-24"));
        repository.upsert("articles", json.createObjectNode().put("id", "landing").put("category", "MUSIC").put("sourceUrl", landing));
        repository.upsert("articles", json.createObjectNode().put("id", "unknown").put("category", "NEWS").put("sourceUrl", "https://www.rockstargames.com/VI/media"));
        given().get("/api/v1/games/gta-vi/news?size=1").then().statusCode(200).body("total", equalTo(3)).body("items[0].id", equalTo("collection"));
        given().get("/api/v1/games/gta-vi/news?size=1&page=1").then().body("items[0].id", equalTo("album"));
        given().get("/api/v1/games/gta-vi/news?size=1&page=2").then().body("items[0].id", equalTo("unknown"));
        assertNotNull(repository.read("articles:landing"), "Raw records and direct article links remain available");
        assertEquals(4, repository.count("articles"));
    }
}
