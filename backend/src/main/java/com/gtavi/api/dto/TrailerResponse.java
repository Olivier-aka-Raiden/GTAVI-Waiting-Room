package com.gtavi.api.dto;

import java.time.OffsetDateTime;

/**
 * Trailer information for the API response.
 */
public record TrailerResponse(
    String id,
    String title,
    String mediaType,
    boolean official,
    OffsetDateTime publicationDate,
    String thumbnailUrl,
    String videoUrl,
    String sourceUrl
) {}
