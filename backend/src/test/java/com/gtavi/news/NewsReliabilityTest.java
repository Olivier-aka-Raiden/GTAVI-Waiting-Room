package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NewsReliabilityTest extends RedisBackedTest {
    @Inject ObjectMapper json;
    @Inject NewsRepository repository;
    @Inject NewsProcessor processor;
    @Inject RedisPersistence persistence;
    private static final String BUY="https://store.rockstargames.com/merchandise/future-collection";

    @Test void verifiedArticleHydrationRecoversBodyLinksAndPublicationDate() throws Exception {
        var shell=OfficialPage.parse(NewsPipelineTest.ARTICLE,NewsPipelineTest.fixture("album-article.html"));
        var post=json.readTree(NewsPipelineTest.fixture("article-7599a881942544.json")).path("data").path("post");
        var full=NewswireClient.fromPost(shell,post);
        assertTrue(full.complete());
        assertTrue(full.content().contains("34"));
        assertEquals("2026-09-17",full.publishedAt());
        assertTrue(full.links().stream().anyMatch(link->link.contains("gtavi-thealbum")));
    }
    @Test void archiveDiscoveryResumesCursorWhileCheckingHeadEachRun() throws Exception {
        var calls=new java.util.ArrayList<Integer>();
        var discovery=new NewswireDiscovery();
        discovery.repository=repository; discovery.json=json; discovery.gtaTag=666;
        discovery.client=new NewswireClient(){
            @Override public JsonNode list(int page,Integer tag) {
                if(tag!=null) calls.add(page);
                var result=NewsReliabilityTest.this.json.createObjectNode();
                result.putObject("paging").put("nextPage",page<3);
                result.putArray("results").addObject().put("url","/newswire/article/page-"+page)
                    .put("title","GTA VI announcement").put("created","9/24/26, 11:00 AM");
                return result;
            }
        };
        discovery.discover(); discovery.discover(); discovery.discover();
        assertEquals(List.of(1,1,1,2,1,3),calls);
        assertEquals(1,repository.read("discovery:archive").path("nextPage").asInt());
        assertEquals(3,repository.due(10).size());
    }
    @Test void budgetDefersAndResumesEveryChunkWithoutDiscardingMiddleEvidence() {
        var calls=new AtomicInteger();
        var analyzer=new NewsAnalyzer();
        analyzer.json=json; analyzer.structured=new StructuredProducts();
        analyzer.ai=(url,title,chunk)->{
            calls.incrementAndGet();
            return new AnnouncementExtraction(true,"NEWS","NEWS",chunk.substring(0,Math.min(40,chunk.length())),List.of());
        };
        var page=OfficialPage.parse("https://www.rockstargames.com/VI/future",
            "<h1>GTA VI future announcement</h1><main>"+ "Meaningful source content. ".repeat(4000)+"</main>");
        ObjectNode result=null;
        int rounds=0;
        do {
            var budget=new ExtractionBudget(1,15000);
            result=analyzer.analyze(page,budget,result);
            assertTrue(budget.usedCalls()<=1);
            assertTrue(budget.usedCharacters()<=15000);
            assertTrue(++rounds<30);
        } while(result.path("enrichmentPending").asBoolean());
        assertEquals(result.path("totalChunks").asInt(),calls.get());
        assertEquals(result.path("totalChunks").asInt(),result.path("nextChunk").asInt());
    }
    @Test void articleStoreAndTopicMergeByExplicitProductLinks() {
        processor.process(article("https://www.rockstargames.com/newswire/article/future",true,"PREORDER","CHF",40));
        processor.process(article(BUY,true,"PREORDER","CHF",40));
        processor.process(article("https://www.rockstargames.com/VI/future-collection",true,"PREORDER","CHF",40));
        assertEquals(1,repository.count("articles"));
        assertEquals(1,repository.count("products"));
        assertEquals(1,eventCount("COLLECTIBLE_ANNOUNCED"));
        assertEquals(0,eventCount("COLLECTIBLE_PREORDER_OPENED"));
        assertEquals(3,repository.list("articles",0,20).getFirst().path("sources").size());
    }
    @Test void laterPreorderNotifiesOnceAndEnrichmentStaysQuiet() {
        var announcement=article("https://www.rockstargames.com/newswire/article/later",false,"ANNOUNCED","USD",0);
        processor.process(announcement);
        var opened=article(announcement.path("sourceUrl").asText(),false,"PREORDER","USD",50);
        processor.process(opened);
        opened.put("imageUrl","https://example.com/new.jpg");
        processor.process(opened);
        assertEquals(1,eventCount("COLLECTIBLE_ANNOUNCED"));
        assertEquals(1,eventCount("COLLECTIBLE_PREORDER_OPENED"));
        assertEquals(0,eventCount("PRICE_CHANGED"));
    }
    @Test void bothRestocksAndPriceChangesSurviveRepeatedCycles() {
        String source="https://www.rockstargames.com/newswire/article/cycles";
        for(String availability:List.of("AVAILABLE","OUT_OF_STOCK","AVAILABLE","OUT_OF_STOCK","AVAILABLE"))
            processor.process(article(source,false,availability,"USD",50));
        assertEquals(2,eventCount("BACK_IN_STOCK"));
        assertEquals(2,eventCount("OUT_OF_STOCK"));
        processor.process(article(source,false,"AVAILABLE","USD",60));
        processor.process(article(source,false,"AVAILABLE","USD",50));
        processor.process(article(source,false,"AVAILABLE","USD",60));
        assertEquals(3,eventCount("PRICE_CHANGED"));
    }
    @Test void multipleCurrenciesRemainSeparateOffers() {
        String source="https://www.rockstargames.com/newswire/article/markets";
        processor.process(article(source,true,"PREORDER","CHF",40));
        processor.process(article(source,true,"PREORDER","USD",45));
        var product=repository.list("products",0,10).getFirst();
        assertEquals(2,product.path("offers").size());
        assertEquals(java.util.Set.of("CHF","USD"),java.util.stream.StreamSupport.stream(product.path("offers").spliterator(),false)
            .map(offer->offer.path("currency").asText()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(0,eventCount("PRICE_CHANGED"));
    }
    @Test void unrelatedProductsAndMissingCurrencyNeverCreateInventedPrices() throws Exception {
        var products=json.createArrayNode();
        var page=OfficialPage.parse(BUY,"<h1>GTA VI collectibles</h1>");
        var extractor=new StructuredProducts();
        extractor.extract(json.readTree("{\"@type\":\"Product\",\"name\":\"GTA V album\",\"offers\":{\"price\":30}}"),page,products);
        assertTrue(products.isEmpty());
        extractor.extract(json.readTree("{\"@type\":\"Product\",\"name\":\"GTA VI box\",\"offers\":{\"price\":30}}"),page,products);
        assertFalse(products.get(0).path("offers").get(0).hasNonNull("price"));
    }

    @Test void capturedCollectorArticleTopicAndStoreProduceOneAnnouncement() throws Exception {
        var analyzer = new NewsAnalyzer();
        analyzer.json = json;
        analyzer.structured = new StructuredProducts();
        analyzer.ai = (url, title, content) -> { throw new IllegalStateException("Offline"); };
        String source = "https://www.rockstargames.com/newswire/article/9k2a49ook82o57";
        var shell = OfficialPage.parse(source, NewsPipelineTest.fixture("collector-article.html"));
        var full = NewswireClient.fromPost(shell, json.readTree(NewsPipelineTest.fixture("article-9k2a49ook82o57.json")).path("data").path("post"));
        processor.process(analyzer.analyze(full));
        processor.process(analyzer.analyze(OfficialPage.parse("https://www.rockstargames.com/VI/vice-city-collection", NewsPipelineTest.fixture("collector-topic.html"))));
        processor.process(analyzer.analyze(OfficialPage.parse(NewsPipelineTest.STORE, NewsPipelineTest.fixture("collector-store.html"))));
        assertEquals(1, repository.count("articles"));
        assertEquals(1, eventCount("COLLECTIBLE_ANNOUNCED"));
        assertFalse(repository.list("products", 0, 20).isEmpty());
    }

    @Test void distinctSkusAtOneProductUrlRemainSeparate() throws Exception {
        var products = json.createArrayNode();
        var page = OfficialPage.parse(BUY, "<h1>GTA VI collection</h1>");
        var data = json.readTree("""
            {"hasVariant":[
              {"@type":"Product","name":"GTA VI vinyl","sku":"red","offers":{"price":30,"priceCurrency":"USD"}},
              {"@type":"Product","name":"GTA VI vinyl","sku":"blue","offers":{"price":35,"priceCurrency":"USD"}}
            ]}
            """);
        new StructuredProducts().extract(data, page, products);
        assertEquals(2, products.size());
        assertNotEquals(products.get(0).path("id"), products.get(1).path("id"));
        assertEquals("red", products.get(0).path("offers").get(0).path("variant").asText());
    }

    @Test void materialFactUpdatesNotifyOnceButSameContentEnrichmentDoesNot() {
        var item = article("https://www.rockstargames.com/newswire/article/facts", false, "ANNOUNCED", "USD", 0);
        item.put("category", "NEWS").put("observationHash", "first");
        item.putArray("products");
        processor.process(item);
        item.putArray("facts").addObject().put("id", "feature").put("importance", "MAJOR").put("value", "New feature");
        processor.process(item); // Recovery of more detail from the exact same source observation.
        assertEquals(1, eventCount("MAJOR_OFFICIAL_NEWS"));
        item.put("observationHash", "changed");
        item.withArray("facts").addObject().put("id", "date").put("importance", "MAJOR").put("value", "New date");
        processor.process(item);
        processor.process(item);
        assertEquals(2, eventCount("MAJOR_OFFICIAL_NEWS"));
    }

    @Test void diagnosticFailuresPersistUntilThatPageRecovers() {
        repository.recordCheck("one", "FAILED", "Unavailable", 300);
        repository.recordCheck("two", "COMPLETE", "", 300);
        assertEquals(1, repository.stateCount("FAILED"));
        repository.recordCheck("one", "PENDING", "Resuming", 300);
        assertEquals(0, repository.stateCount("FAILED"));
        assertEquals(1, repository.stateCount("PENDING"));
        repository.recordCheck("one", "COMPLETE", "", 300);
        assertEquals(0, repository.stateCount("PENDING"));
    }

    @Test void sharedNavigationDoesNotMergeSeparateAnnouncements() {
        var first = article("https://www.rockstargames.com/newswire/article/first-box", false, "ANNOUNCED", "USD", 0);
        var second = article("https://www.rockstargames.com/newswire/article/second-box", false, "ANNOUNCED", "USD", 0);
        for (var item : List.of(first, second)) {
            item.putArray("products");
            item.putArray("relationshipLinks");
            item.putArray("links").add("https://www.rockstargames.com/VI/shared-navigation");
            processor.process(item);
        }
        assertEquals(2, repository.count("articles"));
        assertEquals(2, eventCount("COLLECTIBLE_ANNOUNCED"));
    }
    private long eventCount(String type) {
        return persistence.getEvents("GTA_VI",0,100).stream().filter(event->type.equals(event.getEventType())).count();
    }
    private ObjectNode article(String source,boolean announced,String availability,String currency,int price) {
        var article=json.createObjectNode().put("id",OfficialPage.identity(source)).put("sourceUrl",source)
            .put("title","GTA VI future collection").put("description","An official collectible collection.")
            .put("category","COLLECTIBLE").put("importance","MAJOR").put("relevant",true).put("preorderAnnounced",announced);
        article.putArray("links").add(BUY);
        var product=article.putArray("products").addObject().put("id",OfficialPage.identity(BUY))
            .put("name","GTA VI future box").put("category","COLLECTIBLE").put("purchaseUrl",BUY)
            .put("sourceUrl",source).put("availability",availability).put("currency",currency);
        if(price>0) product.put("price",price);
        return article;
    }
}
