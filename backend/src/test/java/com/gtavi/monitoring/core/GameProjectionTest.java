package com.gtavi.monitoring.core;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class GameProjectionTest extends RedisBackedTest {
    @Inject GameProjectionService projections;
    @Inject RedisPersistence persistence;
    @Inject ObjectMapper json;
    @Test void releaseEditionAndVideoUpdatesReachPublicRecords() throws Exception {
        projections.project("ROCKSTAR_MAIN","https://www.rockstargames.com/VI/",
            json.readTree("{\"releaseDate\":\"2026-12-01\",\"editions\":[{\"name\":\"Future Edition\",\"type\":\"SPECIAL\",\"preorderAvailable\":true}]}"));
        assertEquals(LocalDate.of(2026,12,1),persistence.getGame("GTA_VI").getReleaseDate());
        assertTrue(persistence.getEditions("GTA_VI").stream().anyMatch(edition->"Future Edition".equals(edition.getName())));
        var videos=json.readTree("{\"videos\":[{\"title\":\"Future Trailer\",\"mediaType\":\"TRAILER\",\"videoUrl\":\"https://www.youtube.com/watch?v=future\",\"publicationDate\":\"2026-09-26T12:00:00Z\"}]}");
        projections.project("ROCKSTAR_MEDIA","https://www.rockstargames.com/VI/",videos);
        projections.project("ROCKSTAR_MEDIA","https://www.rockstargames.com/VI/",videos);
        assertEquals(1,persistence.getTrailers("GTA_VI",null).stream().filter(video->"Future Trailer".equals(video.getTitle())).count());
        given().get("/api/v1/games/gta-vi").then().statusCode(200)
            .body("release.date",equalTo("2026-12-01")).body("latestTrailer.title",equalTo("Future Trailer"));
    }
}
