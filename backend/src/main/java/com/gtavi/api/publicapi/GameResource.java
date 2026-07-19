package com.gtavi.api.publicapi;

import com.gtavi.api.dto.*;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.Trailer;
import com.gtavi.service.GameService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;

/**
 * Public REST API for game data.
 * All read-only endpoints — no authentication required.
 */
@Path("/api/v1/games")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    GameService gameService;

    /**
     * Home aggregation endpoint — returns everything needed for the home screen.
     */
    @GET
    @Path("/gta-vi")
    public Response getGameOverview() {
        Game game = gameService.getGame("GTA_VI");
        if (game == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(java.util.Map.of("error", "Game not found"))
                .build();
        }

        List<Trailer> trailers = gameService.getTrailers("GTA_VI", "TRAILER");
        List<Edition> editions = gameService.getEditions("GTA_VI");
        List<com.gtavi.domain.ChangeEvent> events = gameService.getEvents("GTA_VI", 0, 5);
        var monitoringHealth = gameService.getMonitoringHealth();

        var overview = new GameOverviewResponse(
            game.getCode(),
            game.getName(),
            new ReleaseInfoResponse(
                game.getReleaseDate(),
                false, // exact time not known yet
                null,  // no official release timestamp
                "LOCAL_MIDNIGHT",
                true,
                game.getOfficialSiteUrl(),
                monitoringHealth.lastSuccessfulAt(),
                game.getLastChangedAt()
            ),
            trailers.isEmpty() ? null : toTrailerResponse(trailers.get(0)),
            trailers.stream().map(this::toTrailerResponse).toList(),
            editions.stream().map(this::toEditionResponse).toList(),
            events.stream().map(this::toEventResponse).toList(),
            new SystemStatusResponse(monitoringHealth.lastRunAt(), monitoringHealth.healthy(),
                monitoringHealth.monitoredSources(), monitoringHealth.healthySources())
        );

        return Response.ok(overview).build();
    }

    /**
     * Returns all official trailers, ordered by publication date descending.
     */
    @GET
    @Path("/gta-vi/trailers")
    public Response getTrailers(@QueryParam("type") String type) {
        List<Trailer> trailers = gameService.getTrailers("GTA_VI", type);
        return Response.ok(trailers.stream().map(this::toTrailerResponse).toList()).build();
    }

    /**
     * Returns all editions with their status.
     */
    @GET
    @Path("/gta-vi/editions")
    public Response getEditions() {
        List<Edition> editions = gameService.getEditions("GTA_VI");
        return Response.ok(editions.stream().map(this::toEditionResponse).toList()).build();
    }

    /**
     * Returns user-visible change events, paginated, newest first.
     */
    @GET
    @Path("/gta-vi/events")
    public Response getEvents(
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("size") @DefaultValue("20") int size
    ) {
        var events = gameService.getEvents("GTA_VI", page, size);
        long total = gameService.getEventCount("GTA_VI");

        return Response.ok(java.util.Map.of(
            "events", events.stream().map(this::toEventResponse).toList(),
            "total", total,
            "page", page,
            "size", size
        )).build();
    }

    // ---- Mapping helpers ----

    private TrailerResponse toTrailerResponse(Trailer t) {
        return new TrailerResponse(
            t.getId(), t.getTitle(), t.getMediaType(), t.isOfficial(),
            t.getPublicationDate(), t.getThumbnailUrl(), t.getVideoUrl(), t.getSourceUrl()
        );
    }

    private EditionResponse toEditionResponse(Edition e) {
        var offers = gameService.getOffers(e.getId()).stream()
            .map(o -> new RetailOfferResponse(
                o.getId(), o.getRetailerCode(),
                getRetailerName(o.getRetailerCode()),
                o.getPlatform(), o.getPrice(), o.getCurrency(),
                o.getAvailabilityStatus(), o.isPreorderAvailable(),
                normalizeOfferUrl(o.getRetailerCode(), o.getUrl()), o.getLastSuccessfulCheckAt()
            ))
            .toList();

        return new EditionResponse(
            e.getId(), e.getName(), e.getNormalizedType(), e.isOfficial(),
            e.getStatus(), e.getDescription(), e.getImageUrl(), offers
        );
    }

    private String getRetailerName(String code) {
        return switch (code) {
            case "PS_STORE" -> "PlayStation Store";
            case "XBOX_STORE" -> "Xbox Store";
            case "ROCKSTAR_STORE" -> "Rockstar Games Store";
            case "AMAZON_FR" -> "Amazon.fr";
            case "GALAXUS" -> "Galaxus";
            case "WOG" -> "WOG.ch";
            default -> "Retailer";
        };
    }

    private String normalizeOfferUrl(String retailerCode, String value) {
        if (value == null || value.isBlank()) return null;
        String baseUrl = switch (retailerCode) {
            case "PS_STORE" -> "https://store.playstation.com/en-ch/";
            case "XBOX_STORE" -> "https://www.xbox.com/en-ch/";
            case "ROCKSTAR_STORE" -> "https://www.rockstargames.com/VI/";
            case "AMAZON_FR" -> "https://www.amazon.fr/";
            case "GALAXUS" -> "https://www.galaxus.ch/";
            case "WOG" -> "https://www.wog.ch/";
            default -> null;
        };
        try {
            URI uri = baseUrl == null ? URI.create(value) : URI.create(baseUrl).resolve(value);
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) return null;
            return uri.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ChangeEventResponse toEventResponse(com.gtavi.domain.ChangeEvent ce) {
        return new ChangeEventResponse(
            ce.getId(), ce.getEventType(), ce.getPriority(),
            ce.getTitle(), ce.getDescription(),
            ce.getOldValue(), ce.getNewValue(),
            ce.getEvidenceUrl(), ce.getDetectedAt()
        );
    }
}
