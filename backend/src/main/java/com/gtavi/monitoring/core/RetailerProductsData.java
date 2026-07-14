package com.gtavi.monitoring.core;

import java.util.List;

/**
 * Structured output from retailer page extraction.
 * Returns a list of GTA VI products found on the retailer search page.
 */
public record RetailerProductsData(
    List<ProductItem> products
) {
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
