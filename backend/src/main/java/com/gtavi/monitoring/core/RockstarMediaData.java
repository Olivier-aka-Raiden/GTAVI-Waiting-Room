package com.gtavi.monitoring.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Structured output from Rockstar media/videos page extraction.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record RockstarMediaData(
    List<VideoItem> videos
) {
    @JsonAutoDetect(fieldVisibility = ANY)
    public record VideoItem(
        String title,
        String mediaType,
        String publicationDate,
        String videoUrl,
        String thumbnailUrl
    ) {}
}
