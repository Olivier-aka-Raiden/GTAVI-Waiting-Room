package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;

@ApplicationScoped
public class OfficialNewsMonitor implements GameSourceMonitor {
    @Inject NewsRepository repository;
    @Inject OfficialPageFetcher fetcher;
    @Inject NewsAnalyzer analyzer;
    @Inject NewsProcessor processor;
    @Inject NewswireDiscovery discovery;
    @Inject NewswireClient newswire;
    @Inject ObjectMapper json;
    @ConfigProperty(name="gtavi.news.pages-per-run",defaultValue="12") int pageLimit;
    @ConfigProperty(name="gtavi.news.ai-calls-per-run",defaultValue="10") int callLimit;
    @ConfigProperty(name="gtavi.news.ai-characters-per-run",defaultValue="100000") int characterLimit;
    @ConfigProperty(name="gtavi.news.enabled",defaultValue="true") boolean enabled;
    @ConfigProperty(name="gtavi.news.seed-urls",defaultValue="https://www.rockstargames.com/VI/,https://www.rockstargames.com/VI/music,https://store.rockstargames.com") List<String> seeds;
    public String sourceCode(){return "ROCKSTAR_NEWS";}
    public String sourceName(){return "Rockstar official news and collectibles";}
    public String sourceUrl(){return "https://www.rockstargames.com/VI/";}
    public boolean isOfficial(){return true;}
    public int checkIntervalSeconds(){return 300;}

    public MonitorResult fetchCurrentState() {
        if(!enabled) return MonitorResult.success(sourceCode(),sourceUrl(),json.createObjectNode().put("disabled",true),null);
        String lease=repository.acquire(sourceCode());
        if(lease==null) return MonitorResult.failure(sourceCode(),sourceUrl(),MonitorStatus.TEMPORARY_FAILURE,"News monitor already running");
        int processed=0, failures=0, incomplete=0, skipped=0;
        var budget=new ExtractionBudget(Math.clamp(callLimit,0,50),Math.clamp(characterLimit,0,1000000));
        try {
            skipped += repository.pruneInvalidPages();
            seeds.forEach(repository::discover);
            try {
                discovery.discover();
                repository.recordCheck("newswire-list","COMPLETE","",300);
            } catch(Exception e) {
                failures++;
                repository.recordCheck("newswire-list","FAILED","Newswire discovery unavailable; automatic retry scheduled",300);
                Log.warn("Newswire discovery failed; continuing queued pages",e);
            }
            for(int i=0;i<Math.clamp(pageLimit,1,50);i++) {
                var due=repository.due(1);
                if(due.isEmpty()) break;
                String url=due.getFirst();
                String id=OfficialPage.identity(url);
                try {
                    repository.assertOwner(sourceCode(),lease);
                    processor.replay(id);
                    OfficialPage page;
                    try { page=fetcher.fetch(url); }
                    catch(Exception fetchFailure) {
                        if(!url.contains("/newswire/article/")) throw fetchFailure;
                        page=newswire.hydrate(OfficialPage.parse(url,""));
                    }
                    if(url.contains("/newswire/article/") && !page.complete()) {
                        try { page=newswire.hydrate(page); }
                        catch(Exception e) { Log.warnf("Full article unavailable; retaining metadata for %s",url); }
                    }
                    for(String link:page.links()) if(discoverable(link)) repository.discover(link);
                    boolean pending=false;
                    if(publishable(url)) {
                        ObjectNode article=extract(page,id,budget);
                        repository.assertOwner(sourceCode(),lease);
                        processor.process(article);
                        pending=article.path("enrichmentPending").asBoolean();
                    }
                    int retry=pending?300:publishable(url)?1800:300;
                    repository.recordCheck(id,pending?"PENDING":"COMPLETE",pending?"Details incomplete; resuming automatically":"",retry);
                    repository.reschedule(url,retry);
                    if(pending) incomplete++;
                    processed++;
                } catch(OfficialPageFetcher.NonPageResourceException e) {
                    repository.ignore(url, e.getMessage());
                    skipped++;
                    Log.debugf("Ignoring non-page source %s: %s", url, e.getMessage());
                } catch(Exception e) {
                    failures++;
                    JsonNode old=repository.read("checks:"+id);
                    int attempts=old==null?0:old.path("attempts").asInt();
                    int delay=Math.min(21600,300*(1<<Math.min(attempts,6)));
                    repository.recordCheck(id,"FAILED","Fetch or processing failed; automatic retry scheduled",delay);
                    repository.reschedule(url,delay);
                    Log.warnf(e,"Official news page failed: %s",url);
                }
            }
            var status=json.createObjectNode().put("processed",processed).put("failed",failures)
                .put("enrichmentPending",incomplete).put("skipped",skipped).put("skippedPages",repository.stateCount("SKIPPED"))
                .put("pendingPages",repository.stateCount("PENDING")).put("failedPages",repository.stateCount("FAILED"))
                .put("degraded",repository.stateCount("FAILED")+repository.stateCount("PENDING")>0)
                .put("trackedPages",repository.queuedCount()).put("aiCalls",budget.usedCalls())
                .put("aiInputCharacters",budget.usedCharacters())
                .put("checkedAt",java.time.OffsetDateTime.now().toString());
            repository.write("status",status);
            return MonitorResult.success(sourceCode(),sourceUrl(),status,null);
        } finally { repository.release(sourceCode(),lease); }
    }

    private ObjectNode extract(OfficialPage page,String id,ExtractionBudget budget) {
        String hash=OfficialPage.fingerprint(NewsAnalyzer.VERSION+"\n"+page.content()+"\n"+page.structured()+"\n"+page.links());
        JsonNode cached=repository.read("cache:"+id);
        ObjectNode prior=cached!=null && hash.equals(cached.path("hash").asText())
            && cached.path("article").isObject() ? (ObjectNode)cached.path("article").deepCopy() : null;
        ObjectNode article=prior!=null && !prior.path("enrichmentPending").asBoolean()
            ? prior : analyzer.analyze(page,budget,prior);
        article.put("id",id).put("observationHash",hash);
        JsonNode listing=repository.read("listing:"+id);
        if(article.path("publishedAt").asText().isBlank() && listing!=null)
            article.put("publishedAt",listing.path("publishedAt").asText());
        var cache=json.createObjectNode().put("hash",hash);
        cache.set("article",article);
        repository.cache("cache:"+id,cache);
        // Retain source evidence for replay/auditing independently of the AI output.
        var evidence=json.createObjectNode().put("sourceUrl",page.url()).put("content",page.content())
            .put("structured",page.structured()).put("fetchedAt",java.time.OffsetDateTime.now().toString());
        repository.cache("evidence:"+id,evidence);
        return article;
    }

    static boolean discoverable(String url) {
        return OfficialUrlPolicy.discoverable(url);
    }

    static boolean publishable(String url) {
        return OfficialUrlPolicy.publishable(url);
    }
}