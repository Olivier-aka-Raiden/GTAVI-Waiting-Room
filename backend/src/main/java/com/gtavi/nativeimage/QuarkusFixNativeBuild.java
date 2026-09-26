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
 * Quarkus extensions handle their own native image config (Redis, Firebase, etc).
 *
 * Public classes use class literals; package-private classes use
 * string classNames to bypass Java access checks at compile time.
 * Do not enable RegisterForReflection.serialization here: that mode is for
 * Java object serialization and does not retain the methods and fields that
 * Jackson needs when serializing Redis JSON records.
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
        com.gtavi.domain.NotificationDelivery.class,
        com.gtavi.domain.NotificationDeliveryStatus.class,

        // ── Core classes ──
        RetailerProductsData.class,
        RetailerProductsData.ProductItem.class,
        RockstarEditionsData.class,
        RockstarEditionsData.EditionItem.class,
        RockstarMainData.class,
        RockstarMediaData.class,
        RockstarMediaData.VideoItem.class,

        // Announcement AI output, including every nested record.
        com.gtavi.news.AnnouncementExtraction.class,
        com.gtavi.news.AnnouncementExtraction.Product.class,
        com.gtavi.news.AnnouncementExtraction.Fact.class,

        // ── Service records ──
        GameService.MonitoringHealth.class,
    },
    classNames = {
        // Package-private Google internals that need reflection
        "com.google.common.util.concurrent.AbstractFuture$Waiter",
    }
)
public class QuarkusFixNativeBuild {
}
