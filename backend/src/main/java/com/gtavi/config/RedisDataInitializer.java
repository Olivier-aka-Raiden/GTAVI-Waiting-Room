package com.gtavi.config;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates the idempotent baseline records required by a fresh Redis database. */
@ApplicationScoped
public class RedisDataInitializer {

    @Inject
    RedisPersistence persistence;

    void onStart(@Observes StartupEvent event) {
        seedGame();
        seedEditions();
        seedRetailers();
        seedTrailers();
        seedSourceDefinitions();
        seedInitialEvents();
        Log.info("Upstash Redis seed data verified.");
    }

    private void seedGame() {
        Game game = new Game("GTA_VI", "Grand Theft Auto VI",
            LocalDate.parse("2026-11-19"), "https://www.rockstargames.com/VI/");
        persistence.saveGameIfAbsent(game);
    }

    private void seedEditions() {
        persistence.saveEditionIfAbsent(edition(
            "ed-standard", "Standard Edition", "STANDARD", "PREORDER_AVAILABLE",
            "The base GTA VI experience. Includes the Vintage Vice City Pack pre-order bonus and a free month of GTA+."));
        persistence.saveEditionIfAbsent(edition(
            "ed-ultimate", "Ultimate Edition", "ULTIMATE", "PREORDER_AVAILABLE",
            "An exclusive collection of items threaded across all aspects of Jason and Lucia's story. "
                + "Includes the Vintage Vice City Pack, GTA+, and Ultimate Edition exclusives."));
    }

    private Edition edition(String id, String name, String type, String status, String description) {
        OffsetDateTime now = OffsetDateTime.now();
        Edition edition = new Edition();
        edition.setId(id);
        edition.setGameCode("GTA_VI");
        edition.setName(name);
        edition.setNormalizedType(type);
        edition.setOfficial(true);
        edition.setStatus(status);
        edition.setDescription(description);
        edition.setCreatedAt(now);
        edition.setUpdatedAt(now);
        return edition;
    }

    private void seedRetailers() {
        persistence.saveRetailerIfAbsent(retailer("PS_STORE", "PlayStation Store", "CH", true,
            "https://store.playstation.com/en-ch"));
        persistence.saveRetailerIfAbsent(retailer("XBOX_STORE", "Xbox Store", "CH", true,
            "https://www.xbox.com/en-ch"));
        persistence.saveRetailerIfAbsent(retailer("GALAXUS", "Galaxus", "CH", false,
            "https://www.galaxus.ch"));
        persistence.saveRetailerIfAbsent(retailer("WOG", "WOG.ch", "CH", false,
            "https://www.wog.ch"));
        persistence.saveRetailerIfAbsent(retailer("ROCKSTAR_STORE", "Rockstar Games Store", "US", true,
            "https://www.rockstargames.com"));
        persistence.saveRetailerIfAbsent(retailer("AMAZON_FR", "Amazon.fr", "FR", false,
            "https://www.amazon.fr"));
    }

    private Retailer retailer(String code, String name, String countryCode,
                              boolean official, String baseUrl) {
        OffsetDateTime now = OffsetDateTime.now();
        Retailer retailer = new Retailer();
        retailer.setId("retailer-" + code.toLowerCase().replace('_', '-'));
        retailer.setCode(code);
        retailer.setName(name);
        retailer.setCountryCode(countryCode);
        retailer.setOfficialStore(official);
        retailer.setBaseUrl(baseUrl);
        retailer.setEnabled(true);
        retailer.setCreatedAt(now);
        retailer.setUpdatedAt(now);
        return retailer;
    }

    private void seedTrailers() {
        persistence.saveTrailerIfAbsent(trailer(
            "trailer-1", "Grand Theft Auto VI Trailer 1", "2023-12-04T00:00:00Z",
            "https://www.youtube.com/watch?v=QdBZY2fkU-0",
            "https://img.youtube.com/vi/QdBZY2fkU-0/maxresdefault.jpg"));
        persistence.saveTrailerIfAbsent(trailer(
            "trailer-2", "Grand Theft Auto VI Trailer 2", "2025-05-06T00:00:00Z",
            "https://www.youtube.com/watch?v=VQRLujxTm3c",
            "https://img.youtube.com/vi/VQRLujxTm3c/maxresdefault.jpg"));
    }

    private Trailer trailer(String id, String title, String publicationDate,
                            String videoUrl, String thumbnailUrl) {
        OffsetDateTime now = OffsetDateTime.now();
        Trailer trailer = new Trailer();
        trailer.setId(id);
        trailer.setGameCode("GTA_VI");
        trailer.setTitle(title);
        trailer.setMediaType("TRAILER");
        trailer.setOfficial(true);
        trailer.setPublicationDate(OffsetDateTime.parse(publicationDate));
        trailer.setVideoUrl(videoUrl);
        trailer.setSourceUrl("https://www.rockstargames.com/VI/media/videos");
        trailer.setThumbnailUrl(thumbnailUrl);
        trailer.setDiscoveredAt(now);
        trailer.setCreatedAt(now);
        trailer.setUpdatedAt(now);
        return trailer;
    }

    private void seedSourceDefinitions() {
        source("ROCKSTAR_MAIN", "Rockstar GTA VI Main Page", "https://www.rockstargames.com/VI/",
            true, true, 600, 1);
        source("ROCKSTAR_EDITIONS", "Rockstar GTA VI Editions", "https://www.rockstargames.com/VI/editions",
            true, false, 600, 2);
        source("GALAXUS", "Galaxus", "https://www.galaxus.ch/en/search?q=gta%206",
            false, false, 900, 4);
        source("ROCKSTAR_MEDIA", "Rockstar GTA VI Media", "https://www.rockstargames.com/VI/media/videos",
            true, true, 900, 2);
        source("ROCKSTAR_YOUTUBE", "Rockstar Games YouTube",
            "https://www.youtube.com/feeds/videos.xml?channel_id=UCaWd5_7JhbQBe4dknZhsHJg",
            true, true, 900, 2);
        source("PS_STORE", "PlayStation Store",
            "https://store.playstation.com/en-ch/search/grand%20theft%20auto%20vi",
            true, true, 1800, 3);
        source("XBOX_STORE", "Xbox Store", "https://www.xbox.com/fr-ch/Search/Results?q=GTAVI",
            true, true, 1800, 3);
        source("ROCKSTAR_STORE", "Rockstar Games Store", "https://www.rockstargames.com/VI/editions",
            true, true, 1800, 3);
        source("WOG", "WOG.ch", "https://www.wog.ch/fr/index.cfm/search/searchTerm/GTA%206/orderBy/relevance",
            false, true, 1800, 4);
        source("AMAZON_FR", "Amazon.fr", "https://www.amazon.fr/s?k=grand+theft+auto+vi",
            false, true, 1800, 4);
    }

    private void source(String code, String name, String url, boolean official,
                        boolean enabled, int intervalSeconds, int priority) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("code", code);
        definition.put("name", name);
        definition.put("url", url);
        definition.put("official", official);
        definition.put("enabled", enabled);
        definition.put("checkIntervalSeconds", intervalSeconds);
        definition.put("priority", priority);
        definition.put("createdAt", now.toString());
        definition.put("updatedAt", now.toString());
        persistence.saveSourceDefinitionIfAbsent(definition);
    }

    private void seedInitialEvents() {
        event("event-001", "PREORDER_OPENED", "MAJOR",
            "GTA VI pre-orders opened",
            "Rockstar opened pre-orders for Grand Theft Auto VI on June 25, 2026.",
            null, null,
            "https://www.rockstargames.com/newswire/article/5171972o3ak5oa/pre-order-grand-theft-auto-vi-on-june-25",
            "PREORDER_OPENED:2026-06-25", "2026-06-25T00:00:00Z");
        event("event-002", "RELEASE_DATE_CHANGED", "CRITICAL",
            "Release date confirmed: November 19, 2026",
            "Rockstar confirmed the official release date.", "TBD", "2026-11-19",
            "https://www.rockstargames.com/newswire/article/ak3ak31a49a221/grand-theft-auto-vi-is-now-set-to-launch-november-19-2026",
            "RELEASE_DATE_CHANGED:TBD:2026-11-19", "2025-11-06T14:00:00Z");
        event("event-003", "NEW_TRAILER", "MAJOR",
            "Trailer 2 released", "Rockstar published the second official GTA VI trailer.",
            null, null, "https://www.youtube.com/watch?v=VQRLujxTm3c",
            "NEW_TRAILER:trailer-2", "2025-05-06T00:00:00Z");
    }

    private void event(String id, String eventType, String priority, String title,
                       String description, String oldValue, String newValue,
                       String evidenceUrl, String deduplicationKey, String detectedAt) {
        ChangeEvent event = new ChangeEvent();
        event.setId(id);
        event.setGameCode("GTA_VI");
        event.setEventType(eventType);
        event.setPriority(priority);
        event.setTitle(title);
        event.setDescription(description);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setEvidenceUrl(evidenceUrl);
        event.setDeduplicationKey(deduplicationKey);
        event.setDetectedAt(OffsetDateTime.parse(detectedAt));
        event.setUserVisible(true);
        event.setNotificationEligible(true);
        event.setCreatedAt(OffsetDateTime.parse(detectedAt));
        persistence.saveEventIfAbsent(event);
    }
}

