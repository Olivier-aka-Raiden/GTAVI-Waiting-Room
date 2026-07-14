package com.gtavi.monitoring.core;

import java.util.List;

/**
 * Structured output from Rockstar editions page extraction.
 */
public record RockstarEditionsData(
    List<EditionItem> editions,
    Boolean hasCollectorEdition
) {
    public record EditionItem(
        String name,
        String type,
        String description,
        List<String> features,
        List<String> platforms,
        Boolean preorderAvailable
    ) {}
}
