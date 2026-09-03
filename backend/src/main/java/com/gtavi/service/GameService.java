package com.gtavi.service;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.RetailOffer;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import com.gtavi.persistence.RedisPersistence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.List;

/** Service for reading the public game data stored in Upstash Redis. */
@ApplicationScoped
public class GameService {

    @Inject
    RedisPersistence persistence;

    public Game getGame(String code) {
        return persistence.getGame(code);
    }

    public List<Trailer> getTrailers(String gameCode, String mediaType) {
        return persistence.getTrailers(gameCode, mediaType);
    }

    public List<Edition> getEditions(String gameCode) {
        return persistence.getEditions(gameCode);
    }

    public List<ChangeEvent> getEvents(String gameCode, int page, int size) {
        return persistence.getEvents(gameCode, page, size);
    }

    public long getEventCount(String gameCode) {
        return persistence.getEventCount(gameCode);
    }

    public MonitoringHealth getMonitoringHealth() {
        var health = persistence.getMonitoringHealth();
        return new MonitoringHealth(health.lastRunAt(), health.lastSuccessfulAt(),
            health.monitoredSources(), health.healthySources(), health.healthy());
    }

    public List<Retailer> getRetailers() {
        return persistence.getRetailers();
    }

    public List<RetailOffer> getOffers(String editionId) {
        return persistence.getOffers(editionId);
    }

    public record MonitoringHealth(
        OffsetDateTime lastRunAt,
        OffsetDateTime lastSuccessfulAt,
        int monitoredSources,
        int healthySources,
        boolean healthy
    ) {}
}
