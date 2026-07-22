package com.gtavi.monitoring.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Structured output from Rockstar editions page extraction.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record RockstarEditionsData(
    List<EditionItem> editions,
    Boolean hasCollectorEdition
) {
    @JsonAutoDetect(fieldVisibility = ANY)
    public record EditionItem(
        String name,
        String type,
        String description,
        List<String> features,
        List<String> platforms,
        Boolean preorderAvailable
    ) {}
}
