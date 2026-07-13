package com.gtavi.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a game tracked by the app. Currently only GTA_VI.
 * Stored as a Neo4j node with label :Game.
 */
public class Game {

    private String code;
    private String name;
    private LocalDate releaseDate;
    private String officialSiteUrl;
    private OffsetDateTime lastChangedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Game() {}

    public Game(String code, String name, LocalDate releaseDate, String officialSiteUrl) {
        this.code = code;
        this.name = name;
        this.releaseDate = releaseDate;
        this.officialSiteUrl = officialSiteUrl;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public String getOfficialSiteUrl() { return officialSiteUrl; }
    public void setOfficialSiteUrl(String officialSiteUrl) { this.officialSiteUrl = officialSiteUrl; }
    public OffsetDateTime getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(OffsetDateTime lastChangedAt) { this.lastChangedAt = lastChangedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
