package com.gtavi.monitoring.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Structured output from retailer page extraction.
 * Returns a list of GTA VI products found on the retailer search page.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record RetailerProductsData(
    List<ProductItem> products
) {
    @JsonAutoDetect(fieldVisibility = ANY)
    public record ProductItem(
        String name,
        String edition,
        Double price,
        String currency,
        String availability,
        String url,
        String platform
    ) {}
}
