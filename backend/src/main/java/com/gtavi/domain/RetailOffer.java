package com.gtavi.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An offer from a specific retailer for a specific edition.
 * Stored as Neo4j node with label :RetailOffer.
 */
public class RetailOffer {

    private String id;
    private String editionId;
    private String retailerCode;
    private String platform;
    private String countryCode;
    private BigDecimal price;
    private String currency;
    private String url;
    private String availabilityStatus;
    private boolean preorderAvailable;
    private String stockStatus;
    private OffsetDateTime lastSuccessfulCheckAt;
    private OffsetDateTime lastChangedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RetailOffer() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEditionId() { return editionId; }
    public void setEditionId(String editionId) { this.editionId = editionId; }
    public String getRetailerCode() { return retailerCode; }
    public void setRetailerCode(String retailerCode) { this.retailerCode = retailerCode; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public boolean isPreorderAvailable() { return preorderAvailable; }
    public void setPreorderAvailable(boolean preorderAvailable) { this.preorderAvailable = preorderAvailable; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public OffsetDateTime getLastSuccessfulCheckAt() { return lastSuccessfulCheckAt; }
    public void setLastSuccessfulCheckAt(OffsetDateTime lastSuccessfulCheckAt) { this.lastSuccessfulCheckAt = lastSuccessfulCheckAt; }
    public OffsetDateTime getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(OffsetDateTime lastChangedAt) { this.lastChangedAt = lastChangedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
