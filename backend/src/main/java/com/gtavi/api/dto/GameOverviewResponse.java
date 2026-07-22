package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Complete game overview response — everything needed to render the home screen.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record GameOverviewResponse(
    String code,
    String name,
    ReleaseInfoResponse release,
    TrailerResponse latestTrailer,
    List<TrailerResponse> trailers,
    List<EditionResponse> editions,
    List<ChangeEventResponse> latestEvents,
    SystemStatusResponse systemStatus
) {}
