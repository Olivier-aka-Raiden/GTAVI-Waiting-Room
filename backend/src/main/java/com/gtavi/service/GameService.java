package com.gtavi.service;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.Trailer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for querying game data from Neo4j.
 * Read-only operations — writes go through dedicated services.
 */
@ApplicationScoped
public class GameService {

    @Inject
    Driver driver;

    /**
     * Returns the full game overview including release info, latest trailer, editions, and events.
     */
    public Game getGame(String code) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (g:Game {code: $code}) RETURN g",
                java.util.Map.of("code", code)
            );
            if (result.hasNext()) {
                return mapToGame(result.single());
            }
            return null;
        }
    }

    /**
     * Returns all trailers for a game, ordered by publication date descending.
     */
    public List<Trailer> getTrailers(String gameCode, String mediaType) {
        try (var session = driver.session()) {
            String query;
            java.util.Map<String, Object> params;
            if (mediaType != null) {
                query = "MATCH (t:Trailer {gameCode: $gameCode, mediaType: $mediaType}) RETURN t ORDER BY t.publicationDate DESC";
                params = java.util.Map.of("gameCode", gameCode, "mediaType", mediaType);
            } else {
                query = "MATCH (t:Trailer {gameCode: $gameCode}) RETURN t ORDER BY t.publicationDate DESC";
                params = java.util.Map.of("gameCode", gameCode);
            }
            var result = session.run(query, params);
            return result.list(this::mapToTrailer);
        }
    }

    /**
     * Returns all editions for a game.
     */
    public List<Edition> getEditions(String gameCode) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (e:Edition {gameCode: $gameCode}) RETURN e ORDER BY e.normalizedType",
                java.util.Map.of("gameCode", gameCode)
            );
            return result.list(this::mapToEdition);
        }
    }

    /**
     * Returns user-visible change events, ordered by detection time descending.
     */
    public List<ChangeEvent> getEvents(String gameCode, int page, int size) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (ce:ChangeEvent {gameCode: $gameCode, userVisible: true}) " +
                "RETURN ce ORDER BY ce.detectedAt DESC SKIP $skip LIMIT $limit",
                java.util.Map.of("gameCode", gameCode, "skip", page * size, "limit", size)
            );
            return result.list(this::mapToChangeEvent);
        }
    }

    /**
     * Returns the count of user-visible events (for pagination).
     */
    public long getEventCount(String gameCode) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (ce:ChangeEvent {gameCode: $gameCode, userVisible: true}) RETURN count(ce) AS cnt",
                java.util.Map.of("gameCode", gameCode)
            );
            return result.single().get("cnt").asLong();
        }
    }

    /**
     * Returns the latest successful source check time (for the verification badge).
     */
    public OffsetDateTime getLastSuccessfulCheck() {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (s:SourceSnapshot {successful: true}) RETURN max(s.checkedAt) AS lastCheck"
            );
            if (result.hasNext()) {
                var record = result.single();
                if (!record.get("lastCheck").isNull()) {
                    return record.get("lastCheck").asOffsetDateTime();
                }
            }
            return null;
        }
    }

    // ---- Mapping helpers ----

    private Game mapToGame(Record record) {
        var node = record.get("g").asNode();
        var game = new Game();
        game.setCode(node.get("code").asString());
        game.setName(getStringOrNull(node, "name"));
        if (node.containsKey("releaseDate") && !node.get("releaseDate").isNull()) {
            game.setReleaseDate(node.get("releaseDate").asLocalDate());
        }
        game.setOfficialSiteUrl(getStringOrNull(node, "officialSiteUrl"));
        game.setLastChangedAt(getOffsetDateTimeOrNull(node, "lastChangedAt"));
        game.setCreatedAt(getOffsetDateTimeOrNull(node, "createdAt"));
        game.setUpdatedAt(getOffsetDateTimeOrNull(node, "updatedAt"));
        return game;
    }

    private Trailer mapToTrailer(Record record) {
        var node = record.get("t").asNode();
        var trailer = new Trailer();
        trailer.setId(node.get("id").asString());
        trailer.setGameCode(getStringOrNull(node, "gameCode"));
        trailer.setTitle(getStringOrNull(node, "title"));
        trailer.setMediaType(getStringOrNull(node, "mediaType"));
        trailer.setOfficial(getBooleanOrDefault(node, "official", true));
        trailer.setPublicationDate(getOffsetDateTimeOrNull(node, "publicationDate"));
        trailer.setThumbnailUrl(getStringOrNull(node, "thumbnailUrl"));
        trailer.setVideoUrl(getStringOrNull(node, "videoUrl"));
        trailer.setSourceUrl(getStringOrNull(node, "sourceUrl"));
        trailer.setDiscoveredAt(getOffsetDateTimeOrNull(node, "discoveredAt"));
        trailer.setCreatedAt(getOffsetDateTimeOrNull(node, "createdAt"));
        trailer.setUpdatedAt(getOffsetDateTimeOrNull(node, "updatedAt"));
        return trailer;
    }

    private Edition mapToEdition(Record record) {
        var node = record.get("e").asNode();
        var edition = new Edition();
        edition.setId(node.get("id").asString());
        edition.setGameCode(getStringOrNull(node, "gameCode"));
        edition.setName(getStringOrNull(node, "name"));
        edition.setNormalizedType(getStringOrNull(node, "normalizedType"));
        edition.setOfficial(getBooleanOrDefault(node, "official", false));
        edition.setStatus(getStringOrNull(node, "status"));
        edition.setDescription(getStringOrNull(node, "description"));
        edition.setImageUrl(getStringOrNull(node, "imageUrl"));
        edition.setAnnouncedAt(getOffsetDateTimeOrNull(node, "announcedAt"));
        edition.setCreatedAt(getOffsetDateTimeOrNull(node, "createdAt"));
        edition.setUpdatedAt(getOffsetDateTimeOrNull(node, "updatedAt"));
        return edition;
    }

    private ChangeEvent mapToChangeEvent(Record record) {
        var node = record.get("ce").asNode();
        var event = new ChangeEvent();
        event.setId(node.get("id").asString());
        event.setGameCode(getStringOrNull(node, "gameCode"));
        event.setSourceCode(getStringOrNull(node, "sourceCode"));
        event.setEventType(getStringOrNull(node, "eventType"));
        event.setPriority(getStringOrNull(node, "priority"));
        event.setTitle(getStringOrNull(node, "title"));
        event.setDescription(getStringOrNull(node, "description"));
        event.setOldValue(getStringOrNull(node, "oldValue"));
        event.setNewValue(getStringOrNull(node, "newValue"));
        event.setEvidenceUrl(getStringOrNull(node, "evidenceUrl"));
        event.setDeduplicationKey(getStringOrNull(node, "deduplicationKey"));
        event.setDetectedAt(getOffsetDateTimeOrNull(node, "detectedAt"));
        event.setUserVisible(getBooleanOrDefault(node, "userVisible", true));
        event.setNotificationEligible(getBooleanOrDefault(node, "notificationEligible", false));
        event.setCreatedAt(getOffsetDateTimeOrNull(node, "createdAt"));
        return event;
    }

    private String getStringOrNull(org.neo4j.driver.types.Node node, String key) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            return node.get(key).asString();
        }
        return null;
    }

    private OffsetDateTime getOffsetDateTimeOrNull(org.neo4j.driver.types.Node node, String key) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            try {
                return node.get(key).asOffsetDateTime();
            } catch (Exception e) {
                // Handle ZonedDateTime or other temporal types
                return node.get(key).asZonedDateTime().toOffsetDateTime();
            }
        }
        return null;
    }

    private boolean getBooleanOrDefault(org.neo4j.driver.types.Node node, String key, boolean defaultValue) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            return node.get(key).asBoolean();
        }
        return defaultValue;
    }
}
