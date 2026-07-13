package com.gtavi.api.dto;

import java.util.List;

/**
 * Edition with retailer offers.
 */
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
