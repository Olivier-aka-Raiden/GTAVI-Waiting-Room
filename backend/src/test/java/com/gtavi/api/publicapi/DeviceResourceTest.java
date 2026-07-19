package com.gtavi.api.publicapi;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class DeviceResourceTest {

    @Inject
    Driver driver;

    @Test
    void disablingNotificationsUpdatesServerEligibility() {
        String installationId = "disable-test-installation";

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "installationId", installationId,
                "pushToken", "test-push-token",
                "platform", "WEB",
                "locale", "en-CH",
                "appVersion", "1.0.0"))
            .when().post("/api/v1/devices")
            .then().statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("notificationsEnabled", false))
            .when().put("/api/v1/devices/" + installationId)
            .then().statusCode(200);

        try (var session = driver.session()) {
            boolean enabled = session.run("""
                MATCH (d:DeviceInstallation {installationId: $id})
                RETURN d.notificationsEnabled AS enabled
                """, Map.of("id", installationId))
                .single().get("enabled").asBoolean();
            assertFalse(enabled);
        }
    }
}
