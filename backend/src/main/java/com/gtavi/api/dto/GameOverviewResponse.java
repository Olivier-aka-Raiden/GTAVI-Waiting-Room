package com.gtavi.api.dto;

import java.util.List;

/**
 * Complete game overview response — everything needed to render the home screen.
 */
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
