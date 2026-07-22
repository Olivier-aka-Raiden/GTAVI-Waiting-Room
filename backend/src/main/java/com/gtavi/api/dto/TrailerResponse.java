package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Trailer information for the API response.
 */
@JsonAutoDetect(fieldVisibility = ANY)
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
