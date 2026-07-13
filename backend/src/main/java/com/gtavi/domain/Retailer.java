package com.gtavi.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A retailer selling GTA VI. Stored as Neo4j node with label :Retailer.
 */
public class Retailer {

    private String id;
    private String code;
    private String name;
    private String countryCode;
    private boolean officialStore;
    private String baseUrl;
    private boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Retailer() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public boolean isOfficialStore() { return officialStore; }
    public void setOfficialStore(boolean officialStore) { this.officialStore = officialStore; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
