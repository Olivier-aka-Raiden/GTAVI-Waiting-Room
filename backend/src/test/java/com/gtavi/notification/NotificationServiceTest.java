package com.gtavi.notification;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.NotificationDeliveryStatus;
import com.gtavi.notification.fcm.FcmHttpSender;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationServiceTest {

    @Inject
    RedisPersistence persistence;

    private final List<String> registeredInstallations = new ArrayList<>();

    @AfterEach
    void deactivateTestDevices() {
        registeredInstallations.forEach(persistence::deactivateDevice);
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

        FakeFcmSender sender = new FakeFcmSender();
        sender.failOnce.add(secondToken);
        NotificationService service = service(sender, 3, 100);
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
        String firstToken = "token-batch-first-" + unique;
        String secondToken = "token-batch-second-" + unique;
        register(firstInstallation, firstToken);
        register(secondInstallation, secondToken);

        FakeFcmSender sender = new FakeFcmSender();
        NotificationService service = service(sender, 3, 1);
        ChangeEvent event = event("batch-" + unique);

        assertEquals(2, service.saveEventAndQueue(event).deliveriesQueued());
        assertEquals(1, service.processPendingDeliveries());
        assertEquals(1, sender.calls.values().stream().mapToInt(Integer::intValue).sum());

        assertEquals(1, service.processPendingDeliveries());
        assertEquals(1, sender.calls.get(firstToken));
        assertEquals(1, sender.calls.get(secondToken));
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), firstInstallation).status());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), secondInstallation).status());
    }

    @Test
    void senderExceptionForOneDeviceDoesNotStopOtherDeliveries() {
        String unique = UUID.randomUUID().toString();
        String failingInstallation = "outbox-throwing-" + unique;
        String healthyInstallation = "outbox-healthy-" + unique;
        String failingToken = "token-throwing-" + unique;
        String healthyToken = "token-healthy-" + unique;
        register(failingInstallation, failingToken);
        register(healthyInstallation, healthyToken);

        FakeFcmSender sender = new FakeFcmSender();
        sender.throwOnce.add(failingToken);
        NotificationService service = service(sender, 3, 100);
        ChangeEvent event = event("throwing-" + unique);

        assertEquals(2, service.saveEventAndQueue(event).deliveriesQueued());
        assertEquals(1, service.processPendingDeliveries());
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), failingInstallation).status());
        assertEquals(NotificationDeliveryStatus.SENT,
            persistence.getNotificationDelivery(event.getId(), healthyInstallation).status());

        assertEquals(1, service.processPendingDeliveries());
        assertEquals(2, sender.calls.get(failingToken));
        assertEquals(1, sender.calls.get(healthyToken));
    }

    @Test
    void invalidTokenIsTerminalAndDeactivatesTheDevice() {
        String unique = UUID.randomUUID().toString();
        String installationId = "outbox-invalid-" + unique;
        String token = "token-invalid-" + unique;
        register(installationId, token);
        FakeFcmSender sender = new FakeFcmSender();
        sender.invalid.add(token);
        NotificationService service = service(sender, 3, 100);
        ChangeEvent event = event("invalid-" + unique);

        assertTrue(service.saveEventAndQueue(event).created());
        assertEquals(0, service.processPendingDeliveries());

        assertEquals(NotificationDeliveryStatus.INVALID_TOKEN,
            persistence.getNotificationDelivery(event.getId(), installationId).status());
        assertFalse(persistence.isDeviceNotificationsEnabled(installationId));
    }

    @Test
    void transientFailureBecomesDeadAfterConfiguredAttempts() {
        String unique = UUID.randomUUID().toString();
        String installationId = "outbox-dead-" + unique;
        String token = "token-dead-" + unique;
        register(installationId, token);
        FakeFcmSender sender = new FakeFcmSender();
        sender.alwaysFail.add(token);
        NotificationService service = service(sender, 2, 100);
        ChangeEvent event = event("dead-" + unique);

        assertTrue(service.saveEventAndQueue(event).created());
        service.processPendingDeliveries();
        assertEquals(NotificationDeliveryStatus.RETRY,
            persistence.getNotificationDelivery(event.getId(), installationId).status());
        service.processPendingDeliveries();

        var delivery = persistence.getNotificationDelivery(event.getId(), installationId);
        assertEquals(NotificationDeliveryStatus.DEAD, delivery.status());
        assertEquals(2, delivery.attempts());
        assertEquals(2, sender.calls.get(token));
    }

    private NotificationService service(FakeFcmSender sender, int attempts, int batchSize) {
        NotificationService service = new NotificationService();
        service.persistence = persistence;
        service.fcmSender = sender;
        service.maxAttempts = attempts;
        service.retryBaseSeconds = 0;
        service.retryMaxSeconds = 0;
        service.outboxBatchSize = batchSize;
        service.deliveryLeaseSeconds = 30;
        return service;
    }

    private void register(String installationId, String token) {
        persistence.registerDevice(installationId, token, "WEB", "en", "1");
        registeredInstallations.add(installationId);
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
        final Map<String, Integer> calls = new HashMap<>();
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
