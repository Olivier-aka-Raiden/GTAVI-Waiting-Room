package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.domain.Edition;
import com.gtavi.domain.RetailOffer;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regional retailer boxes used to become edition cards of their own and lost their price
 * whenever the page did not spell out the currency.
 */
@QuarkusTest
class RetailerEditionGroupingTest extends RedisBackedTest {

    private static final String SOURCE = "WOG";
    private static final String URL =
        "https://www.wog.ch/fr/index.cfm/search/searchTerm/GTA%206/orderBy/relevance";

    @Inject ObjectMapper mapper;
    @Inject MonitoringOrchestrator orchestrator;
    @Inject RedisPersistence persistence;

    private JsonNode listing;

    @BeforeEach
    void replaceRemoteBoundaries() {
        listing = products(true);
        QuarkusMock.installMockForType(new HttpFetcher() {
            @Override
            public String fetch(String url) throws IOException {
                assertEquals(URL, url, "Only the explicitly requested monitor should run");
                return "<html>GTA VI listings</html>";
            }
        }, HttpFetcher.class);
        QuarkusMock.installMockForType(new AiExtractionService() {
            @Override
            public ExtractionResult extractFromHtml(String content, String sourceType) {
                assertEquals("retailer", sourceType);
                return ExtractionResult.complete(listing, content.length());
            }
        }, AiExtractionService.class);
    }

    @Test
    void regionalBoxesJoinTheStandardEditionAndKeepTheirObservedPrice() {
        var summary = orchestrator.runCheck(Set.of(SOURCE));

        assertEquals(1, summary.successfulSources());
        List<RetailOffer> offers = persistence.getOffers(standardEditionId());
        assertEquals(2, offers.size(), "Both observed platforms remain separate offers");
        assertEquals(Set.of("PS5", "XSX"), offers.stream().map(RetailOffer::getPlatform)
            .collect(java.util.stream.Collectors.toSet()));
        for (RetailOffer offer : offers) {
            assertEquals(0, new BigDecimal("72.90").compareTo(offer.getPrice()),
                "An observed price must survive an unstated currency");
            assertEquals("CHF", offer.getCurrency(), "The retailer's market currency quotes the price");
        }
        assertEquals(2, persistence.getEditions("GTA_VI").size(),
            "Retailer product names must not become edition cards");
    }

    @Test
    void anIncompleteReobservationKeepsTheVerifiedPrice() {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());

        // The next extraction reports the same listings without a price or currency.
        listing = products(false);
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());

        List<RetailOffer> offers = persistence.getOffers(standardEditionId());
        assertEquals(2, offers.size());
        for (RetailOffer offer : offers) {
            assertEquals(0, new BigDecimal("72.90").compareTo(offer.getPrice()),
                "A verified price survives an incomplete re-observation");
            assertEquals("CHF", offer.getCurrency());
        }
    }

    private String standardEditionId() {
        Edition standard = persistence.getEditions("GTA_VI").stream()
            .filter(edition -> "STANDARD".equals(edition.getNormalizedType())).findFirst().orElseThrow();
        return standard.getId();
    }

    private JsonNode products(boolean withPrice) {
        var data = mapper.createObjectNode();
        var products = data.putArray("products");
        for (String platform : List.of("PS5", "XSX")) {
            var product = products.addObject()
                .put("name", "Grand Theft Auto 6 -FR- (Code in a Box)")
                .put("edition", "STANDARD")
                .put("platform", platform)
                .put("availability", "PREORDER")
                .put("url", "https://www.wog.ch/fr/index.cfm/details/product/25282"
                    + ("PS5".equals(platform) ? "1" : "2") + "-Grand-Theft-Auto-6-FR-Code-in-a-Box");
            if (withPrice) product.put("price", 72.90);
        }
        return data;
    }
}
