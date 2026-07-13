package com.gtavi.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Official trailer or video for the game.
 * Stored as Neo4j node with label :Trailer.
 */
public class Trailer {

    private String id;
    private String gameCode;
    private String externalId;
    private String title;
    private String mediaType;
    private boolean official;
    private OffsetDateTime publicationDate;
    private String thumbnailUrl;
    private String videoUrl;
    private String sourceUrl;
    private OffsetDateTime discoveredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Trailer() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGameCode() { return gameCode; }
    public void setGameCode(String gameCode) { this.gameCode = gameCode; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public OffsetDateTime getPublicationDate() { return publicationDate; }
    public void setPublicationDate(OffsetDateTime publicationDate) { this.publicationDate = publicationDate; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public OffsetDateTime getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(OffsetDateTime discoveredAt) { this.discoveredAt = discoveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
