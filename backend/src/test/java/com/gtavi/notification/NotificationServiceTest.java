package com.gtavi.notification;

import com.gtavi.config.RedisBackedTest;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.NotificationDeliveryStatus;
import com.gtavi.notification.fcm.FcmHttpSender;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationServiceTest extends RedisBackedTest {

    @Inject
    RedisPersistence persistence;

    @Inject
    NotificationService service;

    private FakeFcmSender sender;

    @BeforeEach
    void replaceOnlyTheRemoteFcmBoundary() {
        sender = new FakeFcmSender();
        QuarkusMock.installMockForType(sender, FcmHttpSender.class);
    }

    @Test
    void partialFailureRetriesOnlyTheFailedInstallation() {
        String unique = UUID.randomUUID().toString();
        String firstInstallation = "outbox-first-" + unique;
        String secondInstallation = "outbox-second-" + unique;
        String firstToken = "token-first-" + unique;
        String secondToken = "token-second-" + unique;
        register(firstInstallation, firstToken);
        register(secondInstallation, secondToken);

        sender.failOnce.add(secondToken);
        ChangeEvent event = event(unique);

        var queued = service.saveEventAndQueue(event);
        assertTrue(queued.created());
        assertEquals(2, queued.deliveriesQueued());
        assertEquals(1, service.processPendingDeliveries());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), firstInstallation).status());
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), secondInstallation).status());

        assertEquals(1, service.processPendingDeliveries());
        assertEquals(1, sender.calls.get(firstToken));
        assertEquals(2, sender.calls.get(secondToken));
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), secondInstallation).status());

        ChangeEvent duplicate = event(unique);
        assertFalse(service.saveEventAndQueue(duplicate).created());
        assertEquals(event.getId(), duplicate.getId());
        assertEquals(0, service.processPendingDeliveries());
        assertEquals(1, sender.calls.get(firstToken));
    }

    @Test
    void unfinishedBatchResumesWithoutResendingCompletedDeliveries() {
        String unique = UUID.randomUUID().toString();
        String firstInstallation = "outbox-batch-first-" + unique;
        String secondInstallation = "outbox-batch-second-" + unique;
        String thirdInstallation = "outbox-batch-third-" + unique;
        String firstToken = "token-batch-first-" + unique;
        String secondToken = "token-batch-second-" + unique;
        String thirdToken = "token-batch-third-" + unique;
        register(firstInstallation, firstToken);
        register(secondInstallation, secondToken);
        register(thirdInstallation, thirdToken);
        ChangeEvent event = event("batch-" + unique);

        assertEquals(3, service.saveEventAndQueue(event).deliveriesQueued());
        assertEquals(2, service.processPendingDeliveries());
        assertEquals(2, sender.calls.values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, service.processPendingDeliveries());
        assertEquals(1, sender.calls.get(firstToken));
        assertEquals(1, sender.calls.get(secondToken));
        assertEquals(1, sender.calls.get(thirdToken));
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), firstInstallation).status());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), secondInstallation).status());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), thirdInstallation).status());
        assertEquals(0, service.processPendingDeliveries());
    }

    @Test
    void senderExceptionForOneDeviceDoesNotStopOtherDeliveries() {
        String unique = UUID.randomUUID().toString();
        // Equal due times are ordered by the event/installation key in Redis.
        String failingInstallation = "outbox-0-throwing-" + unique;
        String healthyInstallation = "outbox-1-healthy-" + unique;
        String failingToken = "token-throwing-" + unique;
        String healthyToken = "token-healthy-" + unique;
        register(failingInstallation, failingToken);
        register(healthyInstallation, healthyToken);

        sender.throwOnce.add(failingToken);
        ChangeEvent event = event("throwing-" + unique);

        assertEquals(2, service.saveEventAndQueue(event).deliveriesQueued());
        assertEquals(1, service.processPendingDeliveries());
        assertEquals(List.of(failingToken, healthyToken), sender.calls.keySet().stream().toList(),
            "The healthy recipient must be processed after the sender throws");
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), failingInstallation).status());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), healthyInstallation).status());

        assertEquals(1, service.processPendingDeliveries());
        assertEquals(2, sender.calls.get(failingToken));
        assertEquals(1, sender.calls.get(healthyToken));
    }

    @Test
    void invalidTokenIsTerminalWithoutBlockingAHealthyDevice() {
        String unique = UUID.randomUUID().toString();
        String installationId = "outbox-invalid-" + unique;
        String token = "token-invalid-" + unique;
        String healthyInstallation = "outbox-valid-" + unique;
        String healthyToken = "token-valid-" + unique;
        register(installationId, token);
        register(healthyInstallation, healthyToken);
        sender.invalid.add(token);
        ChangeEvent event = event("invalid-" + unique);

        assertTrue(service.saveEventAndQueue(event).created());
        assertEquals(1, service.processPendingDeliveries());
        assertEquals(List.of(token, healthyToken), sender.calls.keySet().stream().toList(),
            "An invalid token must not stop the following healthy delivery");

        assertEquals(NotificationDeliveryStatus.INVALID_TOKEN,
            persistence.getNotificationDelivery(event.getId(), installationId).status());
        assertFalse(persistence.isDeviceNotificationsEnabled(installationId));
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), healthyInstallation).status());
        assertEquals(0, service.processPendingDeliveries());
        assertEquals(1, sender.calls.get(token));
        assertEquals(1, sender.calls.get(healthyToken));
    }

    @Test
    void transientFailureBecomesDeadAfterConfiguredAttempts() {
        String unique = UUID.randomUUID().toString();
        String installationId = "outbox-dead-" + unique;
        String token = "token-dead-" + unique;
        register(installationId, token);
        sender.alwaysFail.add(token);
        ChangeEvent event = event("dead-" + unique);

        assertTrue(service.saveEventAndQueue(event).created());
        service.processPendingDeliveries();
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), installationId).status());
        service.processPendingDeliveries();
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), installationId).status());
        service.processPendingDeliveries();

        var delivery = persistence.getNotificationDelivery(event.getId(), installationId);
        assertEquals(NotificationDeliveryStatus.DEAD, delivery.status());
        assertEquals(3, delivery.attempts());
        assertEquals(3, sender.calls.get(token));
        assertEquals(0, service.processPendingDeliveries());
    }

    private void register(String installationId, String token) {
        persistence.registerDevice(installationId, token, "WEB", "en", "1");
    }

    private ChangeEvent event(String unique) {
        ChangeEvent event = new ChangeEvent();
        event.setGameCode("GTA_VI");
        event.setEventType("NEW_TRAILER");
        event.setPriority("MAJOR");
        event.setTitle("New trailer");
        event.setDescription("A new trailer is available.");
        event.setDeduplicationKey("outbox-test:" + unique);
        event.setDetectedAt(OffsetDateTime.now());
        event.setUserVisible(true);
        event.setNotificationEligible(true);
        return event;
    }

    static final class FakeFcmSender extends FcmHttpSender {
        final Map<String, Integer> calls = new LinkedHashMap<>();
        final Set<String> failOnce = new HashSet<>();
        final Set<String> throwOnce = new HashSet<>();
        final Set<String> alwaysFail = new HashSet<>();
        final Set<String> invalid = new HashSet<>();

        @Override
        public String send(String token, String title, String body, Map<String, String> data) {
            int call = calls.merge(token, 1, Integer::sum);
            if (throwOnce.contains(token) && call == 1) {
                throw new IllegalStateException("simulated sender failure");
            }
            if (invalid.contains(token)) return "INVALID_TOKEN";
            if (alwaysFail.contains(token) || (failOnce.contains(token) && call == 1)) return null;
            return "message-" + token + "-" + call;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
