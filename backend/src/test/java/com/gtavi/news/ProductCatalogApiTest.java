package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.config.RedisBackedTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ProductCatalogApiTest extends RedisBackedTest {
    @Inject NewsRepository repository;
    @Inject ObjectMapper json;

    @Test void existingProductionObservationsAreGroupedBeforePagination() throws Exception {
        var raw = json.readTree(getClass().getResourceAsStream("/fixtures/incident-products-2026-09-26.json"));
        for (var item : raw.path("items")) repository.upsert("products", (ObjectNode)item);
        given().get("/api/v1/games/gta-vi/news/products?size=100").then().statusCode(200)
            .body("items.findAll { it.category == 'ALBUM' }.size()", equalTo(1))
            .body("items.find { it.category == 'ALBUM' }.offers.size()", equalTo(3))
            .body("items.findAll { it.category == 'COLLECTIBLE' }.size()", equalTo(1));
        given().get("/api/v1/games/gta-vi/news/products?size=1").then().statusCode(200)
            .body("items.size()", equalTo(1)).body("total", greaterThan(1));
        org.junit.jupiter.api.Assertions.assertEquals(raw.path("items").size(), repository.count("products"),
            "Read reconciliation must retain raw evidence and pending replay inputs");
    }
}
