package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.RetailOffer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OfferIdentityTest {
    @Test void onlyUnreservedEscapesAndTrackingAreNormalized() {
        assertEquals("https://www.wog.ch/fr/index.cfm/details/product/253144-Grand-Theft-Auto-6",
            OfferIdentity.url("https://www.wog.ch/fr/index.cfm/details/product/253144%2DGrand%2DTheft%2DAuto%2D6?utm_source=test"));
        assertNotEquals(OfferIdentity.url("https://example.com/a%2Fb"), OfferIdentity.url("https://example.com/a/b"));
        assertNotEquals(OfferIdentity.url("https://example.com/p?sku=red"), OfferIdentity.url("https://example.com/p?sku=blue"));
    }
    @Test void productionWogOffersRemoveEncodedDuplicatesAndSearchResults() throws Exception {
        var json = new ObjectMapper();
        var raw = json.readTree(getClass().getResourceAsStream("/fixtures/incident-editions-2026-09-26.json"));
        var offers = new ArrayList<RetailOffer>();
        for (var item : raw.get(0).path("offers")) {
            var offer = new RetailOffer();
            offer.setId(item.path("id").asText());
            offer.setRetailerCode(item.path("retailerCode").asText());
            offer.setPlatform(item.path("platform").asText());
            offer.setCurrency(item.path("currency").asText());
            offer.setUrl(item.path("url").asText());
            offer.setLastSuccessfulCheckAt(OffsetDateTime.parse(item.path("lastSuccessfulCheckAt").asText()));
            if (item.hasNonNull("price")) offer.setPrice(item.path("price").decimalValue());
            offers.add(offer);
        }
        var distinct = OfferIdentity.distinct(offers).stream().filter(o -> "WOG".equals(o.getRetailerCode())).toList();
        assertEquals(6, distinct.size(), "Three actual WOG products per platform must remain");
        assertTrue(distinct.stream().allMatch(o -> new BigDecimal("72.9").compareTo(o.getPrice()) == 0));
        assertTrue(distinct.stream().noneMatch(o -> o.getUrl().contains("/search/")));
    }
    @Test void validatorRejectsSearchLinksAndDeduplicatesEncodedListings() throws Exception {
        var json = new ObjectMapper();
        var data = json.createObjectNode();
        var products = data.putArray("products");
        for (String path : List.of("253144-Grand-Theft-Auto-6", "253144%2DGrand%2DTheft%2DAuto%2D6")) {
            products.addObject().put("name","Grand Theft Auto VI").put("edition","STANDARD")
                .put("platform","PS5").put("url","https://www.wog.ch/fr/index.cfm/details/product/"+path)
                .put("price",72.9).put("currency","CHF");
        }
        products.addObject().put("name","Grand Theft Auto VI").put("platform","UNKNOWN")
            .put("url","https://www.wog.ch/fr/index.cfm/search/searchTerm/GTA%206");
        var result = new RetailerProductValidator().validate("WOG","https://www.wog.ch/",data);
        assertEquals(1, result.path("products").size());
    }
}
