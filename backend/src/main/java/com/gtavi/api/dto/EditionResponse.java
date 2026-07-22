package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Edition with retailer offers.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record EditionResponse(
    String id,
    String name,
    String normalizedType,
    boolean official,
    String status,
    String description,
    String imageUrl,
    List<RetailOfferResponse> offers
) {}
