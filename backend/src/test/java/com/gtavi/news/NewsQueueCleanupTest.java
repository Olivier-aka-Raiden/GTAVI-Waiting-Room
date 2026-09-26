package com.gtavi.news;

import com.gtavi.config.RedisBackedTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NewsQueueCleanupTest extends RedisBackedTest {
    @Inject NewsRepository repository;
    @Inject io.quarkus.redis.datasource.RedisDataSource redis;
    @Inject com.fasterxml.jackson.databind.ObjectMapper json;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "gtavi.redis.key-prefix") String prefix;
    private static final String ROOT = "https://www.rockstargames.com/VI/";

    private void legacy(String url) {
        String id = OfficialPage.identity(url);
        redis.value(String.class).set(prefix + ":news:url:" + id, url);
        repository.reschedule(url, 21600);
        repository.recordCheck(id, "FAILED", "Legacy failure", 21600);
    }

    @Test
    void cleanupRemovesFutureAssetRetriesAndClearsTheirFailureState() {
        legacy(ROOT + "_next/static/media/edition.jpg");
        legacy(ROOT + "ja-JP");
        repository.discover(ROOT + "vice-city-collection");
        assertEquals(2, repository.pruneInvalidPages());
        assertEquals(0, repository.stateCount("FAILED"));
        assertEquals(2, repository.stateCount("SKIPPED"));
        assertEquals(1, repository.queuedCount());
        assertEquals(java.util.List.of(ROOT + "vice-city-collection"), repository.due(1));
    }

    @Test
    void cleanupIsBoundedAndContinuesOnFollowingRuns() {
        for (int i = 0; i < 105; i++) legacy(ROOT + "cover-" + i + ".jpg");
        assertEquals(100, repository.pruneInvalidPages());
        assertEquals(5, repository.queuedCount());
        assertEquals(5, repository.pruneInvalidPages());
        assertEquals(0, repository.queuedCount());
    }

    @Test
    void ignoredExtensionlessResourcesAreNotImmediatelyRediscovered() {
        String url = ROOT + "download";
        repository.discover(url);
        repository.ignore(url, "image/jpeg");
        repository.discover(url);
        assertEquals(0, repository.queuedCount());
        assertEquals(1, repository.stateCount("SKIPPED"));
        redis.key().del(prefix + ":news:ignored:" + OfficialPage.identity(url));
        repository.discover(url);
        assertEquals(java.util.List.of(url), repository.due(1));
    }

    @Test
    void pendingDeliveriesAreReplayedBeforeRetiringALocalizedPage() {
        String url = ROOT + "ja-JP";
        legacy(url);
        String id = OfficialPage.identity(url);
        repository.write("pending:" + id, json.createObjectNode().put("replay", true));
        assertEquals(0, repository.pruneInvalidPages());
        repository.reschedule(url, -1);
        assertEquals(java.util.List.of(url), repository.due(1));
        repository.delete("pending:" + id);
        assertEquals(1, repository.pruneInvalidPages());
        assertEquals(0, repository.queuedCount());
    }
}

