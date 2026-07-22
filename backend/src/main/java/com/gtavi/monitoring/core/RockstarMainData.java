package com.gtavi.monitoring.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Structured output from Rockstar main page extraction.
 * LangChain4j deserializes LLM JSON into this type automatically.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record RockstarMainData(
    String releaseDate,
    List<String> platforms,
    Boolean preorderAvailable,
    String preorderLabel,
    String headlineStatus
) {}
