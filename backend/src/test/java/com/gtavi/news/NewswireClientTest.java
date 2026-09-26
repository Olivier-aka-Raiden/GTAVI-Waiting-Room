package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class NewswireClientTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void successfulGraphqlEnvelopesAcceptMissingNullAndEmptyErrors() throws Exception {
        for (String errors : new String[] {"", ",\"errors\":null", ",\"errors\":[]"}) {
            var result = json.readTree("{\"data\":{\"post\":{\"title\":\"GTA VI\"}}" + errors + "}");
            assertEquals("GTA VI", NewswireClient.responseData(result).path("post").path("title").asText());
        }
        var fixture = json.readTree(NewsPipelineTest.fixture("article-9k2a49ook82o57.json"));
        assertFalse(NewswireClient.responseData(fixture).path("post").isMissingNode());
    }
    @Test void realErrorsAndInvalidDataStillFail() throws Exception {
        for (String input : new String[] {
                "{\"data\":{},\"errors\":[{\"message\":\"unavailable\"}]}",
                "{\"data\":{},\"errors\":{}}", "{\"data\":null,\"errors\":null}", "{}"}) {
            var response = json.readTree(input);
            assertThrows(IOException.class, () -> NewswireClient.responseData(response));
        }
    }
    @Test void datesPreserveKnownPrecisionAndDoNotInventOffsets() {
        assertEquals("2026-09-24", NewswireClient.publicationDate("9/24/26, 11:00 AM"));
        assertEquals("2026-09-17", NewswireClient.publicationDate("9/17/26, 8:00\u202fAM"));
        assertEquals("2026-09-24", NewswireClient.publicationDate("2026-09-24"));
        assertEquals("2026-09-24T11:00-04:00", NewswireClient.publicationDate("2026-09-24T11:00:00-04:00"));
        assertEquals("", NewswireClient.publicationDate("this week"));
        assertEquals("", NewswireClient.publicationDate(null));
    }
    @Test void dateOnlyArchiveArticlesDoNotBecomeFreshAlerts() {
        var policy = new NewsEventPolicy();
        policy.lookbackDays = 14;
        var article = json.createObjectNode().put("id", "archive").put("category", "MUSIC")
            .put("publishedAt", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(60).toString());
        var events = policy.events(article, null, java.util.List.of(), id -> null, 1);
        assertEquals(1, events.size());
        assertFalse(events.getFirst().isNotificationEligible());
        article.put("publishedAt", java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
        assertTrue(policy.events(article, null, java.util.List.of(), id -> null, 1).getFirst().isNotificationEligible());
    }
}