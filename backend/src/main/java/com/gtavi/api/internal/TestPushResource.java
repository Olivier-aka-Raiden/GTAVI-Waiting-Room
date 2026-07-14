package com.gtavi.api.internal;

import com.gtavi.notification.FcmSender;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.neo4j.driver.Driver;

import java.util.Map;

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
    public Response testPush(
        @HeaderParam("X-Internal-Secret") String secret,
        @QueryParam("installationId") String installationId
    ) {
        if (!sharedSecret.equals("dev-secret-change-me") && !sharedSecret.equals(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "unauthorized")).build();
        }

        try (var session = driver.session()) {
            String query;
            Map<String, Object> params;

            if (installationId != null && !installationId.isEmpty()) {
                query = """
                    MATCH (d:DeviceInstallation {installationId: $id, active: true})
                    WHERE d.pushToken IS NOT NULL AND d.pushToken <> ''
                    RETURN d.installationId AS id, d.pushToken AS token
                    """;
                params = Map.of("id", installationId);
            } else {
                query = """
                    MATCH (d:DeviceInstallation {active: true})
                    WHERE d.pushToken IS NOT NULL AND d.pushToken <> ''
                    RETURN d.installationId AS id, d.pushToken AS token
                    LIMIT 5
                    """;
                params = Map.of();
            }

            var result = session.run(query, params);
            int sent = 0;

            while (result.hasNext()) {
                var row = result.next();
                String token = row.get("token").asString();
                String id = row.get("id").asString();

                String messageId = fcmSender.send(token,
                    "🧪 Test Notification",
                    "This is a test push from GTA VI Waiting Room. If you see this, push works! ✅",
                    Map.of("test", "true", "installationId", id));

                if (messageId != null && !"INVALID_TOKEN".equals(messageId)) {
                    sent++;
                    Log.infof("Test push sent to device %s (msg %s)", id, messageId);
                } else {
                    Log.warnf("Test push FAILED for device %s: %s", id, messageId);
                }
            }

            return Response.ok(Map.of(
                "status", "completed",
                "devicesFound", result != null ? 1 : 0,
                "sent", sent
            )).build();
        } catch (Exception e) {
            Log.error("Test push failed", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage())).build();
        }
    }
}
