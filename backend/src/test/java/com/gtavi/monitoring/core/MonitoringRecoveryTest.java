package com.gtavi.monitoring.core;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.monitoring.diff.DiffEngine;
import com.gtavi.news.NewsRepository;
import com.gtavi.notification.NotificationService;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MonitoringRecoveryTest extends RedisBackedTest {
    @Inject RedisPersistence persistence;
    @Inject NewsRepository processing;
    @Inject NotificationService notifications;
    @Inject RetailerProductValidator validator;
    @Inject ObjectMapper json;
    @Inject GameProjectionService projections;
    @Test void interruptedEventCreationRetainsObservationAndBaselineForReplay() throws Exception {
        String code="ROCKSTAR_MAIN";
        var before=json.readTree("{\"releaseDate\":\"2026-11-19\"}");
        var after=json.readTree("{\"releaseDate\":\"2026-12-01\"}");
        persistence.saveSnapshot(code,"https://www.rockstargames.com/VI/",before,"before",true,null);
        var crash=new AtomicBoolean(true);
        var service=new NotificationService() {
            @Override public QueueResult saveEventAndQueue(com.gtavi.domain.ChangeEvent event) {
                if(crash.getAndSet(false)) throw new IllegalStateException("Interrupted");
                return notifications.saveEventAndQueue(event);
            }
            @Override public int processPendingDeliveries(){return 0;}
        };
        var fetched=new java.util.concurrent.atomic.AtomicInteger();
        GameSourceMonitor monitor=new GameSourceMonitor() {
            public String sourceCode(){return code;}
            public String sourceUrl(){return "https://www.rockstargames.com/VI/";}
            public String sourceName(){return "Fixture";}
            public boolean isOfficial(){return true;}
            public int checkIntervalSeconds(){return 0;}
            public MonitorResult fetchCurrentState(){
                if(fetched.incrementAndGet()>1) throw new AssertionError("Pending observation must be replayed before refetch");
                return MonitorResult.success(code,sourceUrl(),after,null);
            }
        };
        @SuppressWarnings("unchecked")
        Instance<GameSourceMonitor> monitors=(Instance<GameSourceMonitor>)Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{Instance.class},
            (proxy,method,args)->{
                if(method.getName().equals("iterator")) return List.of(monitor).iterator();
                throw new UnsupportedOperationException(method.getName());
            });
        var runner=new MonitoringOrchestrator(persistence,new Normalizer(),new DiffEngine(),service,validator,monitors);
        runner.processing=processing; runner.projections=projections;
        assertEquals(1,runner.runCheck(Set.of(code)).failedSources());
        assertEquals(before,persistence.getLatestSuccessfulSnapshotData(code));
        assertNotNull(processing.read("observation:"+code));
        assertEquals(1,runner.runCheck(Set.of(code)).successfulSources());
        assertEquals(after,persistence.getLatestSuccessfulSnapshotData(code));
        assertNull(processing.read("observation:"+code));
        assertEquals(1,fetched.get());
    }

    @Test void repeatedRetailStockCyclesHaveDistinctOccurrences() throws Exception {
        String code = "PS_STORE";
        var states = List.of("IN_STOCK", "OUT_OF_STOCK", "IN_STOCK", "OUT_OF_STOCK", "IN_STOCK");
        var cursor = new java.util.concurrent.atomic.AtomicInteger();
        GameSourceMonitor monitor = new GameSourceMonitor() {
            public String sourceCode() { return code; }
            public String sourceUrl() { return "https://store.playstation.com/en-ch/product/gta-vi"; }
            public String sourceName() { return "Fixture"; }
            public boolean isOfficial() { return false; }
            public int checkIntervalSeconds() { return 0; }
            public MonitorResult fetchCurrentState() {
                var data = json.createObjectNode();
                data.putArray("products").addObject().put("name", "GTA VI Standard Edition")
                    .put("edition", "STANDARD").put("platform", "PS5").put("url", sourceUrl())
                    .put("currency", "CHF").put("price", 70)
                    .put("availability", states.get(cursor.getAndIncrement()));
                return MonitorResult.success(code, sourceUrl(), data, null);
            }
        };
        @SuppressWarnings("unchecked")
        Instance<GameSourceMonitor> monitors = (Instance<GameSourceMonitor>) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[]{Instance.class}, (proxy, method, args) -> {
                if (method.getName().equals("iterator")) return List.of(monitor).iterator();
                throw new UnsupportedOperationException(method.getName());
            });
        var service = new NotificationService() {
            @Override public QueueResult saveEventAndQueue(com.gtavi.domain.ChangeEvent event) {
                return notifications.saveEventAndQueue(event);
            }
            @Override public int processPendingDeliveries() { return 0; }
        };
        var runner = new MonitoringOrchestrator(persistence, new Normalizer(), new DiffEngine(), service, validator, monitors);
        runner.processing = processing;
        runner.projections = projections;
        for (int i = 0; i < states.size(); i++) assertEquals(1, runner.runCheck(Set.of(code)).successfulSources());
        var events = persistence.getEvents("GTA_VI", 0, 100);
        assertEquals(2, events.stream().filter(event -> "BACK_IN_STOCK".equals(event.getEventType())).count());
        assertEquals(2, events.stream().filter(event -> "OUT_OF_STOCK".equals(event.getEventType())).count());
    }
}
