package com.gtavi.api.internal;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class MonitoringResourceTest {

    @Test
    void rejectsMissingSecret() {
        given()
            .when().get("/internal/jobs/monitoring/status")
            .then().statusCode(401)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void rejectsWrongSecret() {
        given()
            .header("X-Internal-Secret", "wrong-secret")
            .when().get("/internal/jobs/monitoring/status")
            .then().statusCode(401);
    }

    @Test
    void acceptsConfiguredSecret() {
        given()
            .header("X-Internal-Secret", "test-secret")
            .when().get("/internal/jobs/monitoring/status")
            .then().statusCode(200)
            .body("status", equalTo("ok"));
    }
}
