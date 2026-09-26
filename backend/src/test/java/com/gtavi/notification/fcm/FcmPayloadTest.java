package com.gtavi.notification.fcm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class FcmPayloadTest {
    @Test void sendsOneDataPayloadWithAnArticleDeepLink() throws Exception {
        var message=new ObjectMapper().readTree(FcmHttpSender.buildPayload("token","The Album","Vinyl & CD",
            Map.of("eventId","event-1","url","/?news=abc"))).path("message");
        assertFalse(message.has("notification"),"Firebase must not auto-display a second notification");
        assertEquals("The Album",message.path("data").path("title").asText());
        assertEquals("/?news=abc",message.path("data").path("url").asText());
    }
}
