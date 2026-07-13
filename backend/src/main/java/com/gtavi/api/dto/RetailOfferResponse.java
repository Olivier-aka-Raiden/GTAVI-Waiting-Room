package com.gtavi.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A retailer offer for a specific edition.
 */
public record RetailOfferResponse(
    String id,
    String retailerCode,
    String retailerName,
    String platform,
    BigDecimal price,
    String currency,
    String availabilityStatus,
    boolean preorderAvailable,
    String url,
    OffsetDateTime lastSuccessfulCheckAt
) {}
