package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Duplicate alerts came from occurrence identities that changed on every observation,
 * and from pushing items whose verified publication date was already old.
 */
class NewsEventPolicyTest {

    private final ObjectMapper json = new ObjectMapper();
    private final NewsEventPolicy policy = new NewsEventPolicy();

    @Test
    void theSameNewFactsKeepOneOccurrenceIdentityAcrossObservations() {
        policy.lookbackDays = 14;
        ObjectNode previous = article(OffsetDateTime.now().minusDays(1));
        previous.putArray("facts").addObject().put("id", "existing")
            .put("importance", "NEWS").put("subject", "Launch").put("value", "May 26, 2026");
        ObjectNode current = previous.deepCopy();
        current.put("observationHash", "changed");
        current.putArray("facts").addObject().put("id", "details")
            .put("importance", "MAJOR").put("subject", "Features").put("value", "New activities discovered");
        current.putArray("newlyObservedFactIds").add("details");

        var first = policy.events(current, previous, List.of(), id -> null, 1);
        var second = policy.events(current, previous, List.of(), id -> null, 4711);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals("MAJOR_OFFICIAL_NEWS", first.getFirst().getEventType());
        assertEquals(first.getFirst().getDeduplicationKey(), second.getFirst().getDeduplicationKey(),
            "Replaying one observation must keep its existing event identity");
        assertTrue(first.getFirst().isNotificationEligible());
        assertNotNull(first.getFirst().getSourcePublishedAt());
    }

    @Test
    void aVerifiedNewDetailOnAnOldPageCanAlert() {
        policy.lookbackDays = 14;
        ObjectNode previous = article(OffsetDateTime.now().minusDays(40));
        previous.putArray("facts").addObject().put("id", "existing")
            .put("importance", "NEWS").put("subject", "Launch").put("value", "May 26, 2026");
        ObjectNode current = previous.deepCopy();
        current.put("observationHash", "changed");
        current.putArray("facts").addObject().put("id", "details")
            .put("importance", "MAJOR").put("subject", "Features").put("value", "New activities discovered");
        current.putArray("newlyObservedFactIds").add("details");

        var events = policy.events(current, previous, List.of(), id -> null, 2);

        assertEquals(1, events.size());
        assertTrue(events.getFirst().isUserVisible());
        assertTrue(events.getFirst().isNotificationEligible());
        assertTrue(events.getFirst().isSourceChangeVerified());
        assertTrue(events.getFirst().getSourcePublishedAt().isBefore(OffsetDateTime.now().minusDays(30)));
    }

    @Test
    void aFirstSightingWithoutAVerifiedDateIsQuiet() {
        policy.lookbackDays = 14;
        ObjectNode article = article(null);

        var events = policy.events(article, null, List.of(), id -> null, 1);

        assertEquals(1, events.size());
        assertFalse(events.getFirst().isNotificationEligible());
        assertNull(events.getFirst().getSourcePublishedAt());
    }

    @Test
    void changedExtractionOrImportanceWithoutNewSourceEvidenceIsQuiet() {
        policy.lookbackDays = 14;
        ObjectNode previous = article(null).put("importance", "NEWS");
        ObjectNode current = previous.deepCopy().put("importance", "MAJOR").put("observationHash", "changed");
        current.putArray("facts").addObject().put("id", "rephrased")
            .put("importance", "MAJOR").put("subject", "Radio").put("value", "Existing station");
        assertTrue(policy.events(current, previous, List.of(), id -> null, 1).isEmpty());
    }

    @Test
    void commercialTransitionsNeedAChangeInSourceEvidence() {
        ObjectNode previous=article(null);
        ObjectNode current=previous.deepCopy();
        ObjectNode oldProduct=json.createObjectNode().put("id","product");
        oldProduct.putArray("offers").addObject().put("id","offer").put("availability","AVAILABLE").put("price",30);
        ObjectNode product=json.createObjectNode().put("id","product").put("name","Album");
        product.putArray("offers").addObject().put("id","offer").put("availability","OUT_OF_STOCK").put("price",40).put("currency","USD");
        var unverified=policy.events(current,previous,List.of(product),id->oldProduct,1);
        assertEquals(2,unverified.size());
        assertTrue(unverified.stream().noneMatch(event->event.isNotificationEligible()));
        current.put("sourceEvidenceChanged",true);
        var verified=policy.events(current,previous,List.of(product),id->oldProduct,2);
        assertEquals(2,verified.size());
        assertTrue(verified.stream().allMatch(event->event.isNotificationEligible()));
    }

    private ObjectNode article(OffsetDateTime publishedAt) {
        ObjectNode article = json.createObjectNode()
            .put("id", "article")
            .put("sourceUrl", "https://www.rockstargames.com/newswire/article/fixture")
            .put("title", "GTA VI announcement")
            .put("description", "An official announcement.")
            .put("category", "NEWS")
            .put("importance", "MAJOR")
            .put("observationHash", "first");
        if (publishedAt != null) article.put("publishedAt", publishedAt.toString());
        article.putArray("products");
        return article;
    }
}
