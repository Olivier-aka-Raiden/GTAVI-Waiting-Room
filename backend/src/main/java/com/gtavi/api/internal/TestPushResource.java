package com.gtavi.api.internal;

import com.gtavi.notification.FcmSender;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.neo4j.driver.Driver;

import java.util.*;

/**
 * Test endpoint for push notifications — sends a test message
 * to a specific device or to all active devices.
 */
@Path("/internal/test")
@Produces(MediaType.APPLICATION_JSON)
public class TestPushResource {

    @Inject Driver driver;
    @Inject FcmSender fcmSender;

    @ConfigProperty(name = "gtavi.internal.shared-secret", defaultValue = "dev-secret-change-me")
    String sharedSecret;

    @POST
    @Path("/push")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testPush(
        @HeaderParam("X-Internal-Secret") String secret,
        @QueryParam("installationId") String installationId
    ) {
        if (!sharedSecret.equals("dev-secret-change-me") && !sharedSecret.equals(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "unauthorized")).build();
        }

        var results = new ArrayList<Map<String, Object>>();

        try (var session = driver.session()) {
            String query;
            Map<String, Object> params;

            if (installationId != null && !installationId.isEmpty()) {
                query = """
                    MATCH (d:DeviceInstallation {installationId: $id})
                    WHERE d.pushToken IS NOT NULL AND d.pushToken <> ''
                    RETURN d.installationId AS id,
                           d.pushToken AS token,
                           d.notificationsEnabled AS enabled,
                           d.active AS active
                    """;
                params = Map.of("id", installationId);
            } else {
                query = """
                    MATCH (d:DeviceInstallation {active: true})
                    WHERE d.pushToken IS NOT NULL AND d.pushToken <> ''
                    RETURN d.installationId AS id,
                           d.pushToken AS token,
                           d.notificationsEnabled AS enabled,
                           d.active AS active
                    LIMIT 5
                    """;
                params = Map.of();
            }

            var dbResult = session.run(query, params);
            int attempted = 0;
            int sent = 0;

            while (dbResult.hasNext()) {
                var row = dbResult.next();
                String token = row.get("token").asString();
                String id = row.get("id").asString();
                boolean enabled = row.containsKey("enabled") && !row.get("enabled").isNull()
                    && row.get("enabled").asBoolean();
                boolean active = row.containsKey("active") && !row.get("active").isNull()
                    && row.get("active").asBoolean();

                attempted++;
                String fcmResult = fcmSender.send(token,
                    "🧪 Test Notification",
                    "This is a test push from GTA VI Waiting Room. If you see this, push works! ✅",
                    Map.of("test", "true", "installationId", id));

                boolean success = fcmResult != null
                    && !"INVALID_TOKEN".equals(fcmResult)
                    && !"disabled".equals(fcmResult);

                if (success) sent++;

                results.add(Map.of(
                    "installationId", id,
                    "tokenPrefix", token.substring(0, Math.min(8, token.length())) + "...",
                    "notificationsEnabled", enabled,
                    "active", active,
                    "fcmResult", fcmResult != null ? fcmResult : "null",
                    "success", success
                ));

                Log.infof("Test push: device=%s success=%s result=%s", id, success, fcmResult);
            }

            return Response.ok(Map.of(
                "status", "completed",
                "fcmEnabled", fcmSender.isEnabled(),
                "devicesFound", attempted,
                "sent", sent,
                "results", results
            )).build();
        } catch (Exception e) {
            Log.error("Test push failed", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage(),
                    "fcmEnabled", fcmSender.isEnabled())).build();
        }
    }
}
