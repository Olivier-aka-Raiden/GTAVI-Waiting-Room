package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.notification.NotificationService;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class NewsPipelineTest extends RedisBackedTest {
    static final String ARTICLE="https://www.rockstargames.com/newswire/article/7599a881942544";
    static final String STORE="https://store.rockstargames.com/merchandise/gtavi-goodtime-state-vice-city-collection";
    @Inject NewsRepository repository;
    @Inject NewsProcessor processor;
    @Inject NotificationService notifications;
    @Inject RedisPersistence persistence;
    @Inject ObjectMapper json;
    @Inject AnnouncementIdentityResolver identities;
    @Inject ProductOffers offers;
    @Inject NewsEventPolicy policy;
    NewsAnalyzer offlineAnalyzer() {
        var analyzer=new NewsAnalyzer();
        analyzer.json=json; analyzer.structured=new StructuredProducts();
        analyzer.ai=(url,title,text)->{throw new IllegalStateException("Simulated AI outage");};
        return analyzer;
    }
    static String fixture(String name) throws Exception {
        try(var stream=NewsPipelineTest.class.getResourceAsStream("/news/"+name)) {
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test void realAlbumShellIsVisibleAndNotifiedDespiteAiFailure() throws Exception {
        persistence.registerDevice("album-device","test-token","WEB","en","1");
        var article=offlineAnalyzer().analyze(OfficialPage.parse(ARTICLE,fixture("album-article.html")));
        assertTrue(article.path("relevant").asBoolean());
        assertTrue(article.path("enrichmentPending").asBoolean());
        assertEquals("MUSIC",article.path("category").asText());
        processor.process(article);
        processor.process(article);
        assertEquals(1,repository.count("articles"));
        var event=persistence.getEvents("GTA_VI",0,100).stream()
            .filter(e->"MUSIC_ANNOUNCED".equals(e.getEventType())).findFirst().orElseThrow();
        var delivery=persistence.getNotificationDelivery(event.getId(),"album-device");
        assertNotNull(delivery);
        assertEquals("/?news="+article.path("id").asText(),delivery.data().get("url"));
        given().get("/api/v1/games/gta-vi/news").then().statusCode(200).body("total",equalTo(1));
        given().get("/api/v1/games/gta-vi/news/"+article.path("id").asText()).then().statusCode(200)
            .body("title",containsString("Album"));
    }
    @Test void realCollectorProductKeepsImagePriceCurrencyAndGameSoldSeparately() throws Exception {
        var result=offlineAnalyzer().analyze(OfficialPage.parse(STORE,fixture("collector-store.html")));
        assertFalse(result.path("products").isEmpty());
        processor.process(result);
        var product=repository.list("products",0,20).getFirst();
        assertEquals("COLLECTIBLE",product.path("category").asText());
        assertEquals(374.99,product.path("price").asDouble());
        assertEquals("CHF",product.path("currency").asText());
        assertFalse(product.path("gameIncluded").asBoolean(true));
        assertTrue(product.path("imageUrl").asText().startsWith("https://images.ctfassets.net/"));
        assertEquals(STORE,product.path("purchaseUrl").asText());
        assertEquals("PREORDER",product.path("availability").asText());
        given().get("/api/v1/games/gta-vi/news/products").then().statusCode(200).body("total",equalTo(1));
    }
    @Test void officialHomepageDiscoversBothMissedAnnouncementsAndStore() throws Exception {
        var page=OfficialPage.parse("https://www.rockstargames.com/VI/",fixture("official-home.html"));
        assertTrue(page.links().stream().anyMatch(s->s.contains("7599a881942544")));
        assertTrue(page.links().stream().anyMatch(s->s.contains("9k2a49ook82o57")));
        assertTrue(page.links().stream().anyMatch(s->s.contains("gtavi-goodtime-state-vice-city-collection")));
    }
    @Test void projectionFailureRemainsReplayableAndDoesNotDuplicateAnEvent() {
        var article=article("recover","MUSIC");
        var failing=new NewsProcessor();
        failing.repository=repository; failing.json=json; failing.identities=identities; failing.offers=offers; failing.policy=policy;
        failing.notifications=new NotificationService(){
            @Override public QueueResult saveEventAndQueue(com.gtavi.domain.ChangeEvent event) {
                notifications.saveEventAndQueue(event);
                throw new IllegalStateException("Crash after durable outbox write");
            }
        };
        assertThrows(IllegalStateException.class,()->failing.process(article));
        String id=article.path("id").asText();
        assertNotNull(repository.read("pending:"+id));
        long before=persistence.getEventCount("GTA_VI");
        processor.replay(id);
        assertNull(repository.read("pending:"+id));
        assertEquals(before,persistence.getEventCount("GTA_VI"));
        assertEquals(1,repository.count("articles"));
    }
    @Test void preferencesAndHistoricalBackfillAreRespected() {
        persistence.registerDevice("opt-out","token","WEB","en","1");
        persistence.updatePreferences("opt-out",Map.of("majorRockstarNews",false));
        var music=article("opt-out","MUSIC");
        processor.process(music);
        var old=article("old","COLLECTIBLE").put("publishedAt",OffsetDateTime.now().minusDays(60).toString());
        processor.process(old);
        for(var event:persistence.getEvents("GTA_VI",0,100))
            assertNull(persistence.getNotificationDelivery(event.getId(),"opt-out"));
        assertEquals(2,repository.count("articles"));
    }
    @Test void durableDiscoveryDeduplicatesAndReschedulesWithoutLosingNewUrls() {
        repository.discover(ARTICLE+"/slug?utm_source=test");
        repository.discover(ARTICLE);
        assertEquals(1,repository.due(10).size());
        repository.reschedule(ARTICLE,3600);
        assertTrue(repository.due(10).isEmpty());
        repository.discover(STORE);
        assertEquals(List.of(STORE),repository.due(10));
    }
    @Test void sourceLeasePreventsOverlappingRunsAndRejectsStaleOwner() {
        String token=repository.acquire("test");
        assertNotNull(token);
        assertNull(repository.acquire("test"));
        repository.release("test","wrong-token");
        repository.assertOwner("test",token);
        repository.release("test",token);
        assertThrows(IllegalStateException.class,()->repository.assertOwner("test",token));
    }
    @Test void unknownOfficialAnnouncementIsNewsAndUnrelatedGtaVIsRejected() {
        var analyzer=offlineAnalyzer();
        var unknown=analyzer.analyze(OfficialPage.parse(ARTICLE,
            "<meta property='og:title' content='GTA VI introduces a new world activity'><main>A new official announcement.</main>"));
        assertTrue(unknown.path("relevant").asBoolean());
        assertEquals("NEWS",unknown.path("category").asText());
        var unrelated=analyzer.analyze(OfficialPage.parse(ARTICLE,
            "<meta property='og:title' content='GTA V collectible album'><main>Grand Theft Auto V soundtrack.</main>"));
        assertFalse(unrelated.path("relevant").asBoolean());
    }
    @Test void incompleteEnrichmentDoesNotEraseVerifiedProductFields() {
        var product=json.createObjectNode().put("id","a").put("name","GTA VI Vinyl")
            .put("imageUrl","https://example.com/image.jpg").put("price",50).put("currency","USD");
        repository.upsert("products",product);
        repository.upsert("products",json.createObjectNode().put("id","a").putNull("price").putNull("imageUrl"));
        assertEquals(50,repository.read("products:a").path("price").asInt());
        assertEquals("https://example.com/image.jpg",repository.read("products:a").path("imageUrl").asText());
    }
    @Test void realMusicPageProducesLimitedVinylCardsWithoutAi() throws Exception {
        var page=OfficialPage.parse("https://www.rockstargames.com/VI/music",fixture("music-page.html"));
        var article=offlineAnalyzer().analyze(page);
        assertTrue(article.path("products").findValuesAsText("purchaseUrl").stream()
            .anyMatch(url->url.equals("https://gtavithealbum.lnk.to/limitededitionvinyl")));
        var vinyl=java.util.stream.StreamSupport.stream(article.path("products").spliterator(),false)
            .filter(p->p.path("purchaseUrl").asText().endsWith("/limitededitionvinyl")).findFirst().orElseThrow();
        assertEquals("VINYL",vinyl.path("category").asText());
        assertTrue(vinyl.path("limited").asBoolean());
        assertFalse(vinyl.hasNonNull("price"));
        processor.process(article);
        assertTrue(repository.count("products")>=2);
    }
    ObjectNode article(String name,String category) {
        String url="https://www.rockstargames.com/newswire/article/"+name;
        var article=json.createObjectNode().put("id",OfficialPage.identity(url)).put("sourceUrl",url)
            .put("title","GTA VI "+name).put("category",category).put("importance","MAJOR").put("relevant",true);
        article.putArray("products");
        return article;
    }
}
