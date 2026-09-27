package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Trailer;
import com.gtavi.news.OfficialPage;
import com.gtavi.persistence.RedisPersistence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

/** Apply accepted monitor facts to the records consumed by the public app. */
@ApplicationScoped
public class GameProjectionService {
    @Inject RedisPersistence persistence;
    public void project(String source,String sourceUrl,JsonNode data) {
        if("ROCKSTAR_MAIN".equals(source) && data.hasNonNull("releaseDate")) {
            try {
                LocalDate date=LocalDate.parse(data.path("releaseDate").asText());
                var game=persistence.getGame("GTA_VI");
                if(game!=null && !date.equals(game.getReleaseDate())) {
                    game.setReleaseDate(date); game.setLastChangedAt(OffsetDateTime.now()); game.setUpdatedAt(OffsetDateTime.now());
                    persistence.saveGame(game);
                }
            } catch(java.time.format.DateTimeParseException ignored) {}
        }
        if(Set.of("ROCKSTAR_MAIN","ROCKSTAR_STORE","ROCKSTAR_EDITIONS").contains(source))
            for(JsonNode fact:data.path("editions")) edition(fact);
        if(source.startsWith("ROCKSTAR_")) for(JsonNode fact:data.path("videos")) video(fact,sourceUrl);
    }
    /**
     * The base game edition used for retailer listings without a distinct edition label.
     * Ordinary retailer product names (regional boxes, market variants) belong to the
     * standard edition instead of becoming edition cards of their own.
     */
    public String ensureStandardEdition() {
        var editions=persistence.getEditions("GTA_VI");
        var existing=editions.stream()
            .filter(e->"STANDARD".equals(e.getNormalizedType())&&e.isOfficial()).findFirst()
            .or(()->editions.stream().filter(e->"STANDARD".equals(e.getNormalizedType())).findFirst())
            .or(()->editions.stream().filter(e->e.getName()!=null
                &&e.getName().toLowerCase(Locale.ROOT).contains("standard")).findFirst());
        if(existing.isPresent()) return existing.get().getId();
        var edition=new Edition();
        edition.setId("edition-"+OfficialPage.fingerprint("standard edition"));
        edition.setGameCode("GTA_VI"); edition.setName("Standard Edition");
        edition.setNormalizedType("STANDARD"); edition.setOfficial(false); edition.setStatus("ANNOUNCED");
        edition.setCreatedAt(OffsetDateTime.now()); edition.setUpdatedAt(OffsetDateTime.now());
        persistence.saveEdition(edition);
        return edition.getId();
    }

    private void edition(JsonNode fact) {
        String name=fact.path("name").asText("");
        if(name.isBlank()) return;
        Edition edition=persistence.getEditions("GTA_VI").stream()
            .filter(e->name.equalsIgnoreCase(e.getName())).findFirst().orElseGet(Edition::new);
        if(edition.getGameCode()==null) {
            edition.setId("edition-"+OfficialPage.fingerprint(name.toLowerCase(Locale.ROOT)));
            edition.setGameCode("GTA_VI"); edition.setCreatedAt(OffsetDateTime.now()); edition.setAnnouncedAt(OffsetDateTime.now());
        }
        edition.setName(name); edition.setOfficial(true);
        edition.setNormalizedType(fact.path("type").asText("UNKNOWN").toUpperCase(Locale.ROOT));
        if(fact.hasNonNull("description")) edition.setDescription(fact.path("description").asText());
        String image=OfficialPage.resolve("https://www.rockstargames.com",fact.path("imageUrl").asText());
        if(image!=null) edition.setImageUrl(image);
        if(fact.hasNonNull("preorderAvailable")) edition.setStatus(fact.path("preorderAvailable").asBoolean()?"PREORDER_AVAILABLE":"ANNOUNCED");
        else if(edition.getStatus()==null) edition.setStatus("ANNOUNCED");
        edition.setUpdatedAt(OffsetDateTime.now());
        persistence.saveEdition(edition);
    }
    private void video(JsonNode fact,String sourceUrl) {
        String url=OfficialPage.resolve(sourceUrl,fact.path("videoUrl").asText());
        String title=fact.path("title").asText("");
        if(url==null || title.isBlank()) return;
        Trailer trailer=persistence.getTrailers("GTA_VI",null).stream()
            .filter(t->url.equals(t.getVideoUrl())).findFirst().orElseGet(Trailer::new);
        if(trailer.getGameCode()==null) {
            trailer.setId("video-"+OfficialPage.identity(url)); trailer.setGameCode("GTA_VI");
            trailer.setCreatedAt(OffsetDateTime.now()); trailer.setDiscoveredAt(OffsetDateTime.now());
        }
        trailer.setTitle(title); trailer.setVideoUrl(url); trailer.setOfficial(true);
        trailer.setSourceUrl(sourceUrl); trailer.setMediaType(fact.path("mediaType").asText("OTHER_VIDEO"));
        String image=OfficialPage.resolve(sourceUrl,fact.path("thumbnailUrl").asText());
        if(image!=null) trailer.setThumbnailUrl(image);
        try { trailer.setPublicationDate(OffsetDateTime.parse(fact.path("publicationDate").asText())); }
        catch(Exception ignored) {
            try { trailer.setPublicationDate(LocalDate.parse(fact.path("publicationDate").asText()).atStartOfDay().atOffset(java.time.ZoneOffset.UTC)); }
            catch(Exception absent) { if(trailer.getPublicationDate()==null) trailer.setPublicationDate(trailer.getDiscoveredAt()); }
        }
        trailer.setUpdatedAt(OffsetDateTime.now());
        persistence.saveTrailer(trailer);
    }
}
