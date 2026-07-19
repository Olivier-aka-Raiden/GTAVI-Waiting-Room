package com.gtavi.api.publicapi;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test for the Game REST API.
 * Uses Neo4j DevServices — a container is auto-started by quarkus-neo4j.
 */
@QuarkusTest
class GameResourceTest {

    @Test
    void testGameOverview() {
        given()
            .when().get("/api/v1/games/gta-vi")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("code", equalTo("GTA_VI"))
            .body("name", equalTo("Grand Theft Auto VI"))
            .body("release.date", equalTo("2026-11-19"))
            .body("release.official", equalTo(true))
            .body("release.countdownPolicy", equalTo("LOCAL_MIDNIGHT"))
            .body("trailers", hasSize(greaterThanOrEqualTo(2)))
            .body("editions", hasSize(greaterThanOrEqualTo(2)))
            .body("latestEvents", hasSize(greaterThanOrEqualTo(1)))
            .body("systemStatus", notNullValue())
            .body("systemStatus.monitoredSources", equalTo(10))
            .body("systemStatus.healthySources", equalTo(0))
            .body("systemStatus.monitoringHealthy", equalTo(false));
    }

    @Test
    void testTrailersEndpoint() {
        given()
            .when().get("/api/v1/games/gta-vi/trailers")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(2))
            .body("[0].title", containsString("Trailer 2"))
            .body("[0].publicationDate", startsWith("2025-05-06"))
            .body("[0].mediaType", equalTo("TRAILER"));
    }

    @Test
    void testTrailersFilteredByType() {
        given()
            .queryParam("type", "TRAILER")
            .when().get("/api/v1/games/gta-vi/trailers")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2))
            .body("[0].title", containsString("Trailer"));
    }

    @Test
    void testEditionsEndpoint() {
        given()
            .when().get("/api/v1/games/gta-vi/editions")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", greaterThanOrEqualTo(2))
            .body("[0].normalizedType", anyOf(equalTo("STANDARD"), equalTo("ULTIMATE")))
            .body("[0].status", equalTo("PREORDER_AVAILABLE"));
    }

    @Test
    void testEventsEndpoint() {
        given()
            .queryParam("page", 0)
            .queryParam("size", 20)
            .when().get("/api/v1/games/gta-vi/events")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("events", hasSize(greaterThanOrEqualTo(1)))
            .body("total", greaterThanOrEqualTo(1));
    }

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("status", equalTo("ok"));
    }

    @Test
    void testGameNotFound() {
        given()
            .when().get("/api/v1/games/non-existent")
            .then()
            .statusCode(404);
    }

    @Test
    void testTrailersOrderedLatestFirst() {
        var response = given()
            .when().get("/api/v1/games/gta-vi/trailers?type=TRAILER")
            .then()
            .statusCode(200)
            .extract();

        // Trailer 2 (2025-05-06) should come before Trailer 1 (2023-12-04)
        String firstTitle = response.jsonPath().getString("[0].title");
        assert firstTitle.contains("Trailer 2") : "Expected Trailer 2 first, got: " + firstTitle;
    }
}
