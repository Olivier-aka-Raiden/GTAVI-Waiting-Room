package com.gtavi.api.internal;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

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

    @Test
    void snapshotCleanupRejectsMissingSecret() {
        given()
            .when().post("/internal/jobs/cleanup-snapshots")
            .then().statusCode(401)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void snapshotCleanupRejectsWrongSecret() {
        given()
            .header("X-Internal-Secret", "wrong-secret")
            .when().post("/internal/jobs/cleanup-snapshots")
            .then().statusCode(401)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void snapshotCleanupAcceptsConfiguredSecret() {
        given()
            .header("X-Internal-Secret", "test-secret")
            .when().post("/internal/jobs/cleanup-snapshots")
            .then().statusCode(200)
            .body("status", equalTo("completed"))
            .body("retentionDays", equalTo(30))
            .body("cutoff", not(blankOrNullString()));
    }
}
