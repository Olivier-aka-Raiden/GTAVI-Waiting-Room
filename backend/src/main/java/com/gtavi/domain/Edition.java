package com.gtavi.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An edition of the game (Standard, Ultimate, Collector, etc.).
 * Stored as Neo4j node with label :Edition.
 */
public class Edition {

    private String id;
    private String gameCode;
    private String externalKey;
    private String name;
    private String normalizedType;
    private boolean official;
    private String status;
    private String description;
    private String imageUrl;
    private OffsetDateTime announcedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Edition() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGameCode() { return gameCode; }
    public void setGameCode(String gameCode) { this.gameCode = gameCode; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedType() { return normalizedType; }
    public void setNormalizedType(String normalizedType) { this.normalizedType = normalizedType; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public OffsetDateTime getAnnouncedAt() { return announcedAt; }
    public void setAnnouncedAt(OffsetDateTime announcedAt) { this.announcedAt = announcedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
