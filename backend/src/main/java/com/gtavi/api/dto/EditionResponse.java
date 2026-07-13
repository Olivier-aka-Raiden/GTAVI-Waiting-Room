package com.gtavi.api.dto;

/**
 * Edition information for the API response.
 */
public record EditionResponse(
    String id,
    String name,
    String normalizedType,
    boolean official,
    String status,
    String description,
    String imageUrl
) {}
