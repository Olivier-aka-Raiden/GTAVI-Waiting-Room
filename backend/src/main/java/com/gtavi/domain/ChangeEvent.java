package com.gtavi.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A detected change event (new trailer, release date change, edition announced, etc.).
 * Stored as Neo4j node with label :ChangeEvent.
 */
public class ChangeEvent {

    private String id;
    private String gameCode;
    private String sourceCode;
    private String eventType;
    private String priority;
    private String title;
    private String description;
    private String oldValue;
    private String newValue;
    private String evidenceUrl;
    private String deduplicationKey;
    private OffsetDateTime detectedAt;
    private boolean userVisible;
    private boolean notificationEligible;
    private OffsetDateTime createdAt;

    public ChangeEvent() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGameCode() { return gameCode; }
    public void setGameCode(String gameCode) { this.gameCode = gameCode; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getEvidenceUrl() { return evidenceUrl; }
    public void setEvidenceUrl(String evidenceUrl) { this.evidenceUrl = evidenceUrl; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public void setDeduplicationKey(String deduplicationKey) { this.deduplicationKey = deduplicationKey; }
    public OffsetDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(OffsetDateTime detectedAt) { this.detectedAt = detectedAt; }
    public boolean isUserVisible() { return userVisible; }
    public void setUserVisible(boolean userVisible) { this.userVisible = userVisible; }
    public boolean isNotificationEligible() { return notificationEligible; }
    public void setNotificationEligible(boolean notificationEligible) { this.notificationEligible = notificationEligible; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
