package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Revisit the head every run while separately progressing through the archive. */
@ApplicationScoped
public class NewswireDiscovery {
    @Inject NewswireClient client;
    @Inject NewsRepository repository;
    @Inject ObjectMapper json;
    @ConfigProperty(name="gtavi.news.newswire-tag", defaultValue="666") int gtaTag;
    public int discover() throws java.io.IOException {
        int found = ingest(client.list(1, gtaTag), true);
        // The unfiltered head also catches relevant posts whose tagging changes.
        found += ingest(client.list(1, null), false);
        JsonNode state = repository.read("discovery:archive");
        int page = state == null ? 1 : state.path("nextPage").asInt(1);
        JsonNode archive = client.list(page, gtaTag);
        found += ingest(archive, true);
        int next = archive.path("paging").path("nextPage").asBoolean() ? page + 1 : 1;
        repository.write("discovery:archive", json.createObjectNode().put("nextPage", next)
            .put("lastSuccessfulAt", java.time.OffsetDateTime.now().toString()));
        return found;
    }
    int ingest(JsonNode listing, boolean gameScoped) {
        int count = 0;
        for (JsonNode post : listing.path("results")) {
            boolean tagged = false;
            for (JsonNode tag : post.path("primary_tags")) if (tag.path("id").asInt() == gtaTag) tagged = true;
            if (!gameScoped && !tagged && !NewsAnalyzer.related(post.path("title").asText())) continue;
            String url = OfficialPage.resolve("https://www.rockstargames.com", post.path("url").asText());
            if (url == null || !OfficialPage.allowed(url)) continue;
            repository.discover(url);
            var metadata = json.createObjectNode().put("publishedAt", NewswireClient.publicationDate(post.path("created").asText()));
            repository.write("listing:" + OfficialPage.identity(url), metadata);
            repository.recoverPublicationDate(url, metadata.path("publishedAt").asText());
            count++;
        }
        return count;
    }
}
