package com.gtavi.api.publicapi;

import com.gtavi.persistence.RedisPersistence;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

/** Device installation API — anonymous registration, token management, preferences. */
@Path("/api/v1/devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {

    @Inject
    RedisPersistence persistence;

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

        persistence.registerDevice(installationId, pushToken, platform, locale, appVersion);
        Log.infof("Device registered: %s (%s)", installationId, platform);
        return Response.ok(Map.of(
            "installationId", installationId,
            "status", "registered"
        )).build();
    }

    @PUT
    @Path("/{installationId}")
    public Response update(@PathParam("installationId") String installationId,
                           Map<String, Object> body) {
        if (!persistence.updateDevice(installationId, body)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Device not found"))
                .build();
        }
        return Response.ok(Map.of("status", "updated")).build();
    }

    @GET
    @Path("/{installationId}/preferences")
    public Response getPreferences(@PathParam("installationId") String installationId) {
        return Response.ok(persistence.getOrCreatePreferences(installationId)).build();
    }

    @PUT
    @Path("/{installationId}/preferences")
    public Response updatePreferences(@PathParam("installationId") String installationId,
                                      Map<String, Object> body) {
        persistence.updatePreferences(installationId, body);
        return Response.ok(Map.of("status", "updated")).build();
    }
}
