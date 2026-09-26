package com.gtavi.nativeimage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.NotificationDelivery;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NotificationJsonTest {
    @Inject ObjectMapper json;

    @Test
    void notificationRetainsEveryFieldAcrossJsonRoundTrip() throws Exception {
        var delivery = NotificationDelivery.queued("event", "device", "The Album — 音楽",
            "Limited vinyl available", Map.of("url", "https://www.rockstargames.com/VI/music"),
            OffsetDateTime.parse("2026-09-26T07:39:58Z"));
        String encoded = json.writeValueAsString(delivery);
        assertTrue(encoded.contains("eventId"));
        assertEquals(delivery, json.readValue(encoded, NotificationDelivery.class));
    }
}
