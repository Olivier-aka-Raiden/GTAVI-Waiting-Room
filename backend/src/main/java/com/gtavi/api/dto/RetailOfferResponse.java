package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * A retailer offer for a specific edition.
 */
@JsonAutoDetect(fieldVisibility = ANY)
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
