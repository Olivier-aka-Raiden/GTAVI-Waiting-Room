package com.gtavi.monitoring.core;

import java.util.List;

/**
 * Structured output from Rockstar main page extraction.
 * LangChain4j deserializes LLM JSON into this type automatically.
 */
public record RockstarMainData(
    String releaseDate,
    List<String> platforms,
    Boolean preorderAvailable,
    String preorderLabel,
    String headlineStatus
) {}
