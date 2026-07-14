package com.gtavi.monitoring.core;

import java.util.List;

/**
 * Structured output from Rockstar media/videos page extraction.
 */
public record RockstarMediaData(
    List<VideoItem> videos
) {
    public record VideoItem(
        String title,
        String mediaType,
        String publicationDate,
        String videoUrl,
        String thumbnailUrl
    ) {}
}
