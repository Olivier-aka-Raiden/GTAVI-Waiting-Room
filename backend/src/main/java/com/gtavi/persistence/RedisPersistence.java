package com.gtavi.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.RetailOffer;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The application's Redis persistence boundary.
 *
 * Records are stored as ordinary JSON strings, so the implementation only
 * depends on core Redis commands supported by Upstash. Sets and sorted sets
 * replace the lookup and time-ordering behavior previously provided by Cypher.
 */
@ApplicationScoped
public class RedisPersistence {

    private static final String DEFAULT_GAME_CODE = "GTA_VI";
    private static final List<String> PREFERENCE_FIELDS = List.of(
        "collectorEditionAnnouncement",
        "collectorEditionPreorder",
        "releaseDateChanges",
        "newOfficialTrailers",
        "majorRockstarNews",
        "generalNews",
        "priceChanges",
        "outOfStock",
        "backInStock"
    );

    private final ObjectMapper objectMapper;
    private final ValueCommands<String, String> values;
    private final SetCommands<String, String> sets;
    private final SortedSetCommands<String, String> sortedSets;
    private final HashCommands<String, String, String> hashes;
    private final String prefix;

    @Inject
    public RedisPersistence(
        RedisDataSource redis,
        ObjectMapper objectMapper,
        @ConfigProperty(name = "gtavi.redis.key-prefix", defaultValue = "gtavi:v1") String prefix
    ) {
        this.objectMapper = objectMapper;
        this.values = redis.value(String.class);
        this.sets = redis.set(String.class);
        this.sortedSets = redis.sortedSet(String.class);
        this.hashes = redis.hash(String.class);
        this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    // ---- Games, trailers, editions, retailers and offers ----

    public Game getGame(String code) {
        return read(entityKey("game", code), Game.class);
    }

    public void saveGameIfAbsent(Game game) {
        writeIfAbsent(entityKey("game", game.getCode()), game);
    }

    public List<Trailer> getTrailers(String gameCode, String mediaType) {
        List<Trailer> trailers = readSortedEntities(
            indexKey("trailers", "game", gameCode), "trailer", Trailer.class);
        if (mediaType == null) return trailers;
        return trailers.stream()
            .filter(trailer -> mediaType.equals(trailer.getMediaType()))
            .toList();
    }

    public void saveTrailerIfAbsent(Trailer trailer) {
        String key = entityKey("trailer", trailer.getId());
        values.setnx(key, toJson(trailer));
        sortedSets.zadd(indexKey("trailers", "game", trailer.getGameCode()),
            epoch(trailer.getPublicationDate()), trailer.getId());
    }

    public List<Edition> getEditions(String gameCode) {
        List<Edition> editions = readSetEntities(
            indexKey("editions", "game", gameCode), "edition", Edition.class);
        editions.sort(Comparator.comparing(Edition::getNormalizedType,
            Comparator.nullsLast(String::compareTo)));
        return editions;
    }

    public List<String> getEditionIds() {
        return new ArrayList<>(sets.smembers(indexKey("editions", "game", DEFAULT_GAME_CODE)));
    }

    public void saveEditionIfAbsent(Edition edition) {
        String key = entityKey("edition", edition.getId());
        values.setnx(key, toJson(edition));
        sets.sadd(indexKey("editions", "game", edition.getGameCode()), edition.getId());
    }

    public List<Retailer> getRetailers() {
        List<Retailer> retailers = readSetEntities(indexKey("retailers"), "retailer", Retailer.class);
        return retailers.stream()
            .filter(Retailer::isEnabled)
            .sorted(Comparator.comparing(Retailer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public void saveRetailerIfAbsent(Retailer retailer) {
        String key = entityKey("retailer", retailer.getCode());
        values.setnx(key, toJson(retailer));
        sets.sadd(indexKey("retailers"), retailer.getCode());
    }

    public List<RetailOffer> getOffers(String editionId) {
        Set<String> ids = sets.smembers(indexKey("offers", "edition", editionId));
        List<RetailOffer> offers = new ArrayList<>();
        for (String id : ids) {
            JsonNode node = readNode(entityKey("offer", id));
            if (node == null || !node.path("active").asBoolean(true)) continue;
            offers.add(convert(node, RetailOffer.class));
        }
        offers.sort(Comparator
            .comparing(RetailOffer::getRetailerCode, Comparator.nullsLast(String::compareTo))
            .thenComparing(RetailOffer::getPlatform, Comparator.nullsLast(String::compareTo)));
        return offers;
    }

    public void upsertOffer(String id, String editionId, String retailerCode,
                            String platform, String countryCode, BigDecimal price,
                            String currency, String url, String availabilityStatus,
                            boolean preorderAvailable) {
        OffsetDateTime now = OffsetDateTime.now();
        String key = entityKey("offer", id);
        JsonNode previous = readNode(key);
        boolean changed = previous == null
            || !sameText(previous, "price", price == null ? null : price.toPlainString())
            || !sameText(previous, "currency", currency)
            || !sameText(previous, "url", url)
            || !sameText(previous, "availabilityStatus", availabilityStatus);

        ObjectNode offer = previous != null && previous.isObject()
            ? ((ObjectNode) previous).deepCopy() : objectMapper.createObjectNode();
        offer.put("id", id);
        offer.put("editionId", editionId);
        offer.put("retailerCode", retailerCode);
        putNullable(offer, "platform", platform);
        putNullable(offer, "countryCode", countryCode);
        if (price == null) offer.putNull("price"); else offer.put("price", price);
        putNullable(offer, "currency", currency);
        putNullable(offer, "url", url);
        putNullable(offer, "availabilityStatus", availabilityStatus);
        offer.put("preorderAvailable", preorderAvailable);
        offer.put("active", true);
        offer.put("missedChecks", 0);
        offer.put("lastSuccessfulCheckAt", now.toString());
        if (changed) offer.put("lastChangedAt", now.toString());
        if (!offer.hasNonNull("createdAt")) offer.put("createdAt", now.toString());
        offer.put("updatedAt", now.toString());

        writeNode(key, offer);
        sets.sadd(indexKey("offers", "edition", editionId), id);
        sets.sadd(indexKey("offers", "retailer", retailerCode), id);
    }

    public void deactivateOffer(String id) {
        JsonNode current = readNode(entityKey("offer", id));
        if (current == null || !current.isObject()) return;
        ObjectNode offer = ((ObjectNode) current).deepCopy();
        offer.put("active", false);
        offer.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(entityKey("offer", id), offer);
    }

    public void markMissingOffers(String retailerCode, Set<String> seenOfferIds) {
        for (String id : sets.smembers(indexKey("offers", "retailer", retailerCode))) {
            if (seenOfferIds.contains(id)) continue;
            JsonNode current = readNode(entityKey("offer", id));
            if (current == null || !current.isObject()) continue;
            ObjectNode offer = ((ObjectNode) current).deepCopy();
            int missedChecks = offer.path("missedChecks").asInt(0) + 1;
            offer.put("missedChecks", missedChecks);
            if (missedChecks >= 2) offer.put("active", false);
            offer.put("updatedAt", OffsetDateTime.now().toString());
            writeNode(entityKey("offer", id), offer);
        }
    }

    // ---- Change events ----

    public List<ChangeEvent> getEvents(String gameCode, int page, int size) {
        long start = (long) page * size;
        long stop = start + size - 1L;
        List<String> ids = sortedSets.zrange(
            indexKey("events", "game", gameCode, "visible"), start, stop,
            new ZRangeArgs().rev());
        List<ChangeEvent> events = new ArrayList<>();
        for (String id : ids) {
            ChangeEvent event = read(entityKey("event", id), ChangeEvent.class);
            if (event != null) events.add(event);
        }
        return events;
    }

    public long getEventCount(String gameCode) {
        return sortedSets.zcard(indexKey("events", "game", gameCode, "visible"));
    }

    public boolean saveEventIfAbsent(ChangeEvent event) {
        if (event.getGameCode() == null) event.setGameCode(DEFAULT_GAME_CODE);
        if (event.getDetectedAt() == null) event.setDetectedAt(OffsetDateTime.now());
        if (event.getCreatedAt() == null) event.setCreatedAt(OffsetDateTime.now());
        String deduplicationKey = event.getDeduplicationKey() == null
            ? event.getId() : event.getDeduplicationKey();

        String existingId = hashes.hget(indexKey("events", "dedup"), deduplicationKey);
        if (existingId != null) {
            event.setId(existingId);
            return false;
        }
        if (!hashes.hsetnx(indexKey("events", "dedup"), deduplicationKey, event.getId())) {
            event.setId(hashes.hget(indexKey("events", "dedup"), deduplicationKey));
            return false;
        }

        try {
            write(entityKey("event", event.getId()), event);
            if (event.isUserVisible()) {
                sortedSets.zadd(indexKey("events", "game", event.getGameCode(), "visible"),
                    epoch(event.getDetectedAt()), event.getId());
            }
            return true;
        } catch (RuntimeException e) {
            hashes.hdel(indexKey("events", "dedup"), deduplicationKey);
            throw e;
        }
    }

    // ---- Source definitions and monitoring snapshots ----

    public void saveSourceDefinitionIfAbsent(Map<String, Object> source) {
        String code = Objects.toString(source.get("code"));
        String key = entityKey("source", code);
        values.setnx(key, toJson(source));
        sets.sadd(indexKey("sources"), code);
    }

    public MonitoringHealthData getMonitoringHealth() {
        int monitoredSources = 0;
        int healthySources = 0;
        OffsetDateTime lastRunAt = null;
        OffsetDateTime lastSuccessfulAt = null;
        OffsetDateTime oldestRunAt = null;

        for (String sourceCode : sets.smembers(indexKey("sources"))) {
            JsonNode definition = readNode(entityKey("source", sourceCode));
            if (definition == null || !definition.path("enabled").asBoolean(true)) continue;
            monitoredSources++;

            JsonNode latest = latestSnapshot(sourceCode, false);
            if (latest == null) continue;
            OffsetDateTime checkedAt = parseDate(latest, "checkedAt");
            lastRunAt = later(lastRunAt, checkedAt);
            oldestRunAt = earlier(oldestRunAt, checkedAt);
            if (latest.path("successful").asBoolean(false)) {
                healthySources++;
                lastSuccessfulAt = later(lastSuccessfulAt, checkedAt);
            }
        }

        boolean recent = oldestRunAt != null
            && oldestRunAt.isAfter(OffsetDateTime.now().minusHours(2));
        boolean healthy = monitoredSources > 0
            && healthySources == monitoredSources && recent;
        return new MonitoringHealthData(lastRunAt, lastSuccessfulAt,
            monitoredSources, healthySources, healthy);
    }

    public OffsetDateTime getLatestSnapshotTime(String sourceCode) {
        JsonNode latest = latestSnapshot(sourceCode, false);
        return latest == null ? null : parseDate(latest, "checkedAt");
    }

    public JsonNode getLatestSuccessfulSnapshotData(String sourceCode) {
        JsonNode snapshot = latestSnapshot(sourceCode, true);
        if (snapshot == null) return null;
        JsonNode normalized = snapshot.get("normalizedJson");
        if (normalized == null || normalized.isNull()) return null;
        if (!normalized.isTextual()) return normalized;
        String json = normalized.asText();
        if (json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid normalized snapshot JSON for " + sourceCode, e);
        }
    }

    public void saveSnapshot(String sourceCode, String sourceUrl, JsonNode data,
                             String hash, boolean successful, String errorMessage) {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        String id = checkedAt.toInstant().toEpochMilli() + "-" + UUID.randomUUID();
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", id);
        snapshot.put("sourceCode", sourceCode);
        putNullable(snapshot, "sourceUrl", sourceUrl);
        snapshot.put("status", successful ? "SUCCESS" : "FAILURE");
        if (data == null) snapshot.putNull("normalizedJson");
        else snapshot.set("normalizedJson", data);
        putNullable(snapshot, "normalizedHash", hash);
        snapshot.put("checkedAt", checkedAt.toString());
        snapshot.put("successful", successful);
        putNullable(snapshot, "errorMessage", errorMessage);

        writeNode(entityKey("snapshot", id), snapshot);
        sortedSets.zadd(indexKey("snapshots", "source", sourceCode), epoch(checkedAt), id);
        if (successful) {
            sortedSets.zadd(indexKey("snapshots", "successful", sourceCode), epoch(checkedAt), id);
        }
    }

    private JsonNode latestSnapshot(String sourceCode, boolean successfulOnly) {
        String key = successfulOnly
            ? indexKey("snapshots", "successful", sourceCode)
            : indexKey("snapshots", "source", sourceCode);
        List<String> ids = sortedSets.zrange(key, 0, 0, new ZRangeArgs().rev());
        return ids.isEmpty() ? null : readNode(entityKey("snapshot", ids.getFirst()));
    }

    // ---- Devices, preferences and notification deliveries ----

    public void registerDevice(String installationId, String pushToken,
                               String platform, String locale, String appVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        deactivateTokenOwner(pushToken, installationId);

        String key = entityKey("device", installationId);
        JsonNode current = readNode(key);
        ObjectNode device = current != null && current.isObject()
            ? ((ObjectNode) current).deepCopy() : objectMapper.createObjectNode();

        String previousToken = text(device, "pushToken");
        if (previousToken != null && !previousToken.equals(pushToken)) {
            hashes.hdel(indexKey("device-tokens"), previousToken);
        }
        device.put("installationId", installationId);
        device.put("pushToken", pushToken);
        device.put("platform", platform);
        device.put("locale", locale);
        device.put("appVersion", appVersion);
        device.put("notificationsEnabled", true);
        device.put("active", true);
        device.put("lastSeenAt", now.toString());
        if (!device.hasNonNull("createdAt")) device.put("createdAt", now.toString());
        device.put("updatedAt", now.toString());
        writeNode(key, device);
        sets.sadd(indexKey("devices"), installationId);
        hashes.hset(indexKey("device-tokens"), pushToken, installationId);
        ensurePreferences(installationId);
    }

    public boolean updateDevice(String installationId, Map<String, Object> changes) {
        String key = entityKey("device", installationId);
        JsonNode current = readNode(key);
        if (current == null || !current.isObject()) return false;
        ObjectNode device = ((ObjectNode) current).deepCopy();

        if (changes.containsKey("pushToken") && changes.get("pushToken") instanceof String token
            && !token.isBlank()) {
            deactivateTokenOwner(token, installationId);
            String previousToken = text(device, "pushToken");
            if (previousToken != null && !previousToken.equals(token)) {
                hashes.hdel(indexKey("device-tokens"), previousToken);
            }
            device.put("pushToken", token);
            hashes.hset(indexKey("device-tokens"), token, installationId);
        }
        putStringChange(device, changes, "appVersion");
        putStringChange(device, changes, "locale");
        if (changes.containsKey("notificationsEnabled")
            && changes.get("notificationsEnabled") instanceof Boolean enabled) {
            device.put("notificationsEnabled", enabled);
        }
        OffsetDateTime now = OffsetDateTime.now();
        device.put("lastSeenAt", now.toString());
        device.put("updatedAt", now.toString());
        writeNode(key, device);
        return true;
    }

    public Map<String, Boolean> getOrCreatePreferences(String installationId) {
        ensureDeviceStub(installationId);
        ObjectNode preferences = ensurePreferences(installationId);
        return preferenceMap(preferences);
    }

    public void updatePreferences(String installationId, Map<String, Object> changes) {
        ensureDeviceStub(installationId);
        ObjectNode preferences = ensurePreferences(installationId);
        for (String field : PREFERENCE_FIELDS) {
            Object value = changes.get(field);
            if (value instanceof Boolean enabled) preferences.put(field, enabled);
        }
        preferences.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(entityKey("preferences", installationId), preferences);
    }

    public List<DeviceTokenRecord> getEligibleDevices(String preferenceField) {
        List<DeviceTokenRecord> result = new ArrayList<>();
        for (String installationId : sets.smembers(indexKey("devices"))) {
            JsonNode device = readNode(entityKey("device", installationId));
            if (device == null
                || !device.path("active").asBoolean(true)
                || !device.path("notificationsEnabled").asBoolean(false)) continue;
            String token = text(device, "pushToken");
            if (token == null || token.isBlank()) continue;
            JsonNode preferences = readNode(entityKey("preferences", installationId));
            if (preferences == null || preferences.path(preferenceField).asBoolean(defaultPreference(preferenceField))) {
                result.add(new DeviceTokenRecord(installationId, token));
            }
        }
        return result;
    }

    public boolean isDeviceNotificationsEnabled(String installationId) {
        JsonNode device = readNode(entityKey("device", installationId));
        return device != null && device.path("notificationsEnabled").asBoolean(false);
    }

    public void deactivateDevice(String installationId) {
        String key = entityKey("device", installationId);
        JsonNode current = readNode(key);
        if (current == null || !current.isObject()) return;
        ObjectNode device = ((ObjectNode) current).deepCopy();
        device.put("active", false);
        device.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(key, device);
    }

    public boolean isEventAlreadyDelivered(String eventId) {
        return sets.scard(indexKey("deliveries", "event", eventId)) > 0;
    }

    public void recordDelivery(String eventId, String installationId, String messageId) {
        OffsetDateTime now = OffsetDateTime.now();
        String id = eventId + ":" + installationId + ":" + UUID.randomUUID();
        ObjectNode delivery = objectMapper.createObjectNode();
        delivery.put("id", id);
        delivery.put("changeEventId", eventId);
        delivery.put("deviceInstallationId", installationId);
        delivery.put("providerMessageId", messageId);
        delivery.put("status", "SENT");
        delivery.put("sentAt", now.toString());
        delivery.put("createdAt", now.toString());
        writeNode(entityKey("delivery", id), delivery);
        sets.sadd(indexKey("deliveries"), id);
        sets.sadd(indexKey("deliveries", "event", eventId), id);
    }

    // ---- Internal helpers ----

    private void deactivateTokenOwner(String pushToken, String newInstallationId) {
        String otherId = hashes.hget(indexKey("device-tokens"), pushToken);
        if (otherId == null || otherId.equals(newInstallationId)) return;
        String otherKey = entityKey("device", otherId);
        JsonNode current = readNode(otherKey);
        if (current != null && current.isObject()) {
            ObjectNode other = ((ObjectNode) current).deepCopy();
            other.put("active", false);
            other.put("notificationsEnabled", false);
            other.put("updatedAt", OffsetDateTime.now().toString());
            writeNode(otherKey, other);
        }
    }

    private void ensureDeviceStub(String installationId) {
        String key = entityKey("device", installationId);
        JsonNode current = readNode(key);
        OffsetDateTime now = OffsetDateTime.now();
        if (current == null) {
            ObjectNode device = objectMapper.createObjectNode();
            device.put("installationId", installationId);
            device.put("platform", "UNKNOWN");
            device.put("notificationsEnabled", false);
            device.put("active", true);
            device.put("lastSeenAt", now.toString());
            device.put("createdAt", now.toString());
            device.put("updatedAt", now.toString());
            writeNode(key, device);
            sets.sadd(indexKey("devices"), installationId);
        } else if (current.isObject()) {
            ObjectNode device = ((ObjectNode) current).deepCopy();
            device.put("lastSeenAt", now.toString());
            device.put("updatedAt", now.toString());
            writeNode(key, device);
        }
    }

    private ObjectNode ensurePreferences(String installationId) {
        String key = entityKey("preferences", installationId);
        JsonNode current = readNode(key);
        if (current != null && current.isObject()) return ((ObjectNode) current).deepCopy();
        ObjectNode preferences = objectMapper.createObjectNode();
        for (String field : PREFERENCE_FIELDS) {
            preferences.put(field, defaultPreference(field));
        }
        preferences.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(key, preferences);
        return preferences;
    }

    private static boolean defaultPreference(String field) {
        return !Set.of("generalNews", "priceChanges", "outOfStock").contains(field);
    }

    private static Map<String, Boolean> preferenceMap(JsonNode preferences) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String field : PREFERENCE_FIELDS) {
            result.put(field, preferences.path(field).asBoolean(defaultPreference(field)));
        }
        return result;
    }

    private <T> List<T> readSortedEntities(String index, String entityType, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (String id : sortedSets.zrange(index, 0, -1, new ZRangeArgs().rev())) {
            T value = read(entityKey(entityType, id), type);
            if (value != null) result.add(value);
        }
        return result;
    }

    private <T> List<T> readSetEntities(String index, String entityType, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (String id : sets.smembers(index)) {
            T value = read(entityKey(entityType, id), type);
            if (value != null) result.add(value);
        }
        return result;
    }

    private void writeIfAbsent(String key, Object value) {
        values.setnx(key, toJson(value));
    }

    private void write(String key, Object value) {
        values.set(key, toJson(value));
    }

    private void writeNode(String key, JsonNode value) {
        values.set(key, value.toString());
    }

    private <T> T read(String key, Class<T> type) {
        String json = values.get(key);
        if (json == null) return null;
        try {
            return objectMapper.readerFor(type)
                .withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid Redis JSON at key " + key, e);
        }
    }

    private JsonNode readNode(String key) {
        String json = values.get(key);
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid Redis JSON at key " + key, e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.readerFor(type)
                .withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(node);
        } catch (IOException e) {
            throw new IllegalStateException("Could not deserialize Redis record as " + type.getSimpleName(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Redis record", e);
        }
    }

    private String entityKey(String type, String id) {
        return prefix + ":" + type + ":" + id;
    }

    private String indexKey(String... parts) {
        return prefix + ":" + String.join(":", parts);
    }

    private static long epoch(OffsetDateTime value) {
        return value == null ? 0 : value.toInstant().toEpochMilli();
    }

    private static OffsetDateTime parseDate(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Invalid date in Redis field " + field + ": " + value, e);
        }
    }

    private static OffsetDateTime later(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private static OffsetDateTime earlier(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean sameText(JsonNode node, String field, String expected) {
        String actual = text(node, field);
        if ("price".equals(field) && actual != null && expected != null) {
            try {
                return new BigDecimal(actual).compareTo(new BigDecimal(expected)) == 0;
            } catch (NumberFormatException ignored) {
                // Fall through to normal string comparison.
            }
        }
        return Objects.equals(actual, expected);
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    private static void putStringChange(ObjectNode target, Map<String, Object> changes, String field) {
        Object value = changes.get(field);
        if (value instanceof String text) target.put(field, text);
    }

    public record MonitoringHealthData(
        OffsetDateTime lastRunAt,
        OffsetDateTime lastSuccessfulAt,
        int monitoredSources,
        int healthySources,
        boolean healthy
    ) {}

    public record DeviceTokenRecord(String installationId, String token) {}
}

