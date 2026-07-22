package com.gtavi.nativeimage;

import com.gtavi.api.dto.ChangeEventResponse;
import com.gtavi.api.dto.EditionResponse;
import com.gtavi.api.dto.GameOverviewResponse;
import com.gtavi.api.dto.ReleaseInfoResponse;
import com.gtavi.api.dto.RetailOfferResponse;
import com.gtavi.api.dto.SystemStatusResponse;
import com.gtavi.api.dto.TrailerResponse;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.RetailOffer;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import com.gtavi.monitoring.core.RetailerProductsData;
import com.gtavi.monitoring.core.RockstarEditionsData;
import com.gtavi.monitoring.core.RockstarMainData;
import com.gtavi.monitoring.core.RockstarMediaData;
import com.gtavi.service.GameService;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers domain/DTO classes for native image reflection (Jackson serialization).
 * Quarkus extensions handle their own native image config (Neo4j, Firebase, etc).
 *
 * Public classes use class literals; package-private classes use
 * string classNames to bypass Java access checks at compile time.
 */
@RegisterForReflection(
    targets = {
        // ── API DTOs (Jackson serialization) ──
        GameOverviewResponse.class,
        SystemStatusResponse.class,
        ReleaseInfoResponse.class,
        TrailerResponse.class,
        EditionResponse.class,
        RetailOfferResponse.class,
        ChangeEventResponse.class,

        // ── Domain classes ──
        Game.class,
        Trailer.class,
        Edition.class,
        Retailer.class,
        RetailOffer.class,
        ChangeEvent.class,

        // ── Core classes ──
        RetailerProductsData.class,
        RetailerProductsData.ProductItem.class,
        RockstarEditionsData.class,
        RockstarEditionsData.EditionItem.class,
        RockstarMainData.class,
        RockstarMediaData.class,
        RockstarMediaData.VideoItem.class,

        // ── Service records ──
        GameService.MonitoringHealth.class,
    },
    classNames = {
        // Package-private Neo4j/Google internals that need reflection
        "com.google.common.util.concurrent.AbstractFuture$Waiter",
    },
    serialization = true
)
public class QuarkusFixNativeBuild {
}
