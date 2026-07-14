package com.gtavi.api.publicapi;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.neo4j.driver.Driver;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Device installation API — anonymous registration, token management, preferences.
 */
@Path("/api/v1/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {

    @Inject
    Driver driver;

    /**
     * Register or update a device installation.
     */
    @POST
    public Response register(Map<String, Object> body) {
        String installationId = (String) body.getOrDefault("installationId",
            UUID.randomUUID().toString());
        String pushToken = (String) body.get("pushToken");
        String platform = (String) body.getOrDefault("platform", "UNKNOWN");
        String locale = (String) body.getOrDefault("locale", "en");
        String appVersion = (String) body.getOrDefault("appVersion", "1.0.0");

        if (pushToken == null || pushToken.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "pushToken is required"))
                .build();
        }

        try (var session = driver.session()) {
            session.run("""
                MERGE (d:DeviceInstallation {installationId: $installationId})
                SET d.pushToken = $pushToken,
                    d.platform = $platform,
                    d.locale = $locale,
                    d.appVersion = $appVersion,
                    d.notificationsEnabled = coalesce(d.notificationsEnabled, true),
                    d.active = true,
                    d.lastSeenAt = datetime(),
                    d.updatedAt = datetime()
                ON CREATE SET d.createdAt = datetime()
                """, Map.of(
                    "installationId", installationId,
                    "pushToken", pushToken,
                    "platform", platform,
                    "locale", locale,
                    "appVersion", appVersion
                ));

            // Create default preferences if not exist
            session.run("""
                MATCH (d:DeviceInstallation {installationId: $installationId})
                MERGE (d)-[:HAS_PREFERENCES]->(np:NotificationPreference)
                ON CREATE SET
                    np.collectorEditionAnnouncement = true,
                    np.collectorEditionPreorder = true,
                    np.releaseDateChanges = true,
                    np.newOfficialTrailers = true,
                    np.majorRockstarNews = true,
                    np.generalNews = false,
                    np.priceChanges = false,
                    np.outOfStock = false,
                    np.backInStock = true,
                    np.updatedAt = datetime()
                """, Map.of("installationId", installationId));
        }

        Log.infof("Device registered: %s (%s)", installationId, platform);
        return Response.ok(Map.of(
            "installationId", installationId,
            "status", "registered"
        )).build();
    }

    /**
     * Update device (token refresh, last seen).
     */
    @PUT
    @Path("/{installationId}")
    public Response update(@PathParam("installationId") String installationId,
                            Map<String, Object> body) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (d:DeviceInstallation {installationId: $id}) RETURN d",
                Map.of("id", installationId)
            );
            if (!result.hasNext()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Device not found"))
                    .build();
            }

            StringBuilder setClause = new StringBuilder();
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("id", installationId);

            if (body.containsKey("pushToken")) {
                setClause.append("d.pushToken = $pushToken, ");
                params.put("pushToken", body.get("pushToken"));
            }
            if (body.containsKey("appVersion")) {
                setClause.append("d.appVersion = $appVersion, ");
                params.put("appVersion", body.get("appVersion"));
            }
            if (body.containsKey("locale")) {
                setClause.append("d.locale = $locale, ");
                params.put("locale", body.get("locale"));
            }
            setClause.append("d.lastSeenAt = datetime(), d.updatedAt = datetime()");

            session.run(
                "MATCH (d:DeviceInstallation {installationId: $id}) SET " + setClause,
                params
            );
        }

        return Response.ok(Map.of("status", "updated")).build();
    }

    /**
     * Get notification preferences.
     */
    @GET
    @Path("/{installationId}/preferences")
    public Response getPreferences(@PathParam("installationId") String installationId) {
        try (var session = driver.session()) {
            var result = session.run("""
                MATCH (d:DeviceInstallation {installationId: $id})-[:HAS_PREFERENCES]->(np:NotificationPreference)
                RETURN np
                """, Map.of("id", installationId));

            if (result.hasNext()) {
                var node = result.single().get("np").asNode();
                return Response.ok(Map.of(
                    "collectorEditionAnnouncement", getBool(node, "collectorEditionAnnouncement", true),
                    "collectorEditionPreorder", getBool(node, "collectorEditionPreorder", true),
                    "releaseDateChanges", getBool(node, "releaseDateChanges", true),
                    "newOfficialTrailers", getBool(node, "newOfficialTrailers", true),
                    "majorRockstarNews", getBool(node, "majorRockstarNews", true),
                    "generalNews", getBool(node, "generalNews", false),
                    "priceChanges", getBool(node, "priceChanges", false),
                    "outOfStock", getBool(node, "outOfStock", false),
                    "backInStock", getBool(node, "backInStock", true)
                )).build();
            }

            // Device not found — auto-create with defaults (same as first PUT)
            session.run("""
                MERGE (d:DeviceInstallation {installationId: $id})
                ON CREATE SET d.platform = 'UNKNOWN',
                    d.notificationsEnabled = false,
                    d.active = true,
                    d.lastSeenAt = datetime(),
                    d.createdAt = datetime(),
                    d.updatedAt = datetime()
                ON MATCH SET d.lastSeenAt = datetime(), d.updatedAt = datetime()
                """, Map.of("id", installationId));

            session.run("""
                MATCH (d:DeviceInstallation {installationId: $id})
                MERGE (d)-[:HAS_PREFERENCES]->(np:NotificationPreference)
                ON CREATE SET
                    np.collectorEditionAnnouncement = true,
                    np.collectorEditionPreorder = true,
                    np.releaseDateChanges = true,
                    np.newOfficialTrailers = true,
                    np.majorRockstarNews = true,
                    np.generalNews = false,
                    np.priceChanges = false,
                    np.outOfStock = false,
                    np.backInStock = true,
                    np.updatedAt = datetime()
                """, Map.of("id", installationId));

            return Response.ok(Map.of(
                "collectorEditionAnnouncement", true,
                "collectorEditionPreorder", true,
                "releaseDateChanges", true,
                "newOfficialTrailers", true,
                "majorRockstarNews", true,
                "generalNews", false,
                "priceChanges", false,
                "outOfStock", false,
                "backInStock", true
            )).build();
        }
    }

    /**
     * Update notification preferences.
     */
    @PUT
    @Path("/{installationId}/preferences")
    public Response updatePreferences(@PathParam("installationId") String installationId,
                                       Map<String, Object> body) {
        try (var session = driver.session()) {
            // Ensure the DeviceInstallation exists (user may save prefs before enabling push)
            session.run("""
                MERGE (d:DeviceInstallation {installationId: $id})
                ON CREATE SET d.platform = 'UNKNOWN',
                    d.notificationsEnabled = false,
                    d.active = true,
                    d.lastSeenAt = datetime(),
                    d.createdAt = datetime(),
                    d.updatedAt = datetime()
                ON MATCH SET d.lastSeenAt = datetime(), d.updatedAt = datetime()
                """, Map.of("id", installationId));

            session.run("""
                MERGE (d:DeviceInstallation {installationId: $id})
                MERGE (d)-[:HAS_PREFERENCES]->(np:NotificationPreference)
                ON CREATE SET
                    np.collectorEditionAnnouncement = coalesce($collectorAnnounce, true),
                    np.collectorEditionPreorder = coalesce($collectorPreorder, true),
                    np.releaseDateChanges = coalesce($releaseDate, true),
                    np.newOfficialTrailers = coalesce($trailers, true),
                    np.majorRockstarNews = coalesce($majorNews, true),
                    np.generalNews = coalesce($generalNews, false),
                    np.priceChanges = coalesce($priceChanges, false),
                    np.outOfStock = coalesce($outOfStock, false),
                    np.backInStock = coalesce($backInStock, true),
                    np.updatedAt = datetime()
                ON MATCH SET
                    np.collectorEditionAnnouncement = coalesce($collectorAnnounce, np.collectorEditionAnnouncement),
                    np.collectorEditionPreorder = coalesce($collectorPreorder, np.collectorEditionPreorder),
                    np.releaseDateChanges = coalesce($releaseDate, np.releaseDateChanges),
                    np.newOfficialTrailers = coalesce($trailers, np.newOfficialTrailers),
                    np.majorRockstarNews = coalesce($majorNews, np.majorRockstarNews),
                    np.generalNews = coalesce($generalNews, np.generalNews),
                    np.priceChanges = coalesce($priceChanges, np.priceChanges),
                    np.outOfStock = coalesce($outOfStock, np.outOfStock),
                    np.backInStock = coalesce($backInStock, np.backInStock),
                    np.updatedAt = datetime()
                """, Map.of(
                    "id", installationId,
                    "collectorAnnounce", body.get("collectorEditionAnnouncement"),
                    "collectorPreorder", body.get("collectorEditionPreorder"),
                    "releaseDate", body.get("releaseDateChanges"),
                    "trailers", body.get("newOfficialTrailers"),
                    "majorNews", body.get("majorRockstarNews"),
                    "generalNews", body.get("generalNews"),
                    "priceChanges", body.get("priceChanges"),
                    "outOfStock", body.get("outOfStock"),
                    "backInStock", body.get("backInStock")
                ));
        }
        return Response.ok(Map.of("status", "updated")).build();
    }

    private boolean getBool(org.neo4j.driver.types.Node node, String key, boolean defaultVal) {
        if (node.containsKey(key) && !node.get(key).isNull()) {
            return node.get(key).asBoolean();
        }
        return defaultVal;
    }
}
