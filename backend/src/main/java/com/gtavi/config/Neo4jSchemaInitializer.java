package com.gtavi.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

/**
 * Initializes Neo4j constraints, indexes, and seed data on startup.
 * All operations are idempotent (IF NOT EXISTS / MERGE).
 */
@ApplicationScoped
public class Neo4jSchemaInitializer {

    @Inject
    Driver driver;

    void onStart(@Observes StartupEvent event) {
        try (Session session = driver.session()) {
            createConstraints(session);
            createIndexes(session);
            seedGame(session);
            seedEditions(session);
            seedRetailers(session);
            seedTrailers(session);
            seedSourceDefinitions(session);
            seedInitialEvents(session);
            Log.info("Neo4j schema initialized and seed data verified.");
        } catch (Exception e) {
            Log.error("Failed to initialize Neo4j schema", e);
            throw new RuntimeException("Neo4j initialization failed", e);
        }
    }

    private void createConstraints(Session session) {
        session.run("CREATE CONSTRAINT game_code_unique IF NOT EXISTS FOR (g:Game) REQUIRE g.code IS UNIQUE");
        session.run("CREATE CONSTRAINT edition_id_unique IF NOT EXISTS FOR (e:Edition) REQUIRE e.id IS UNIQUE");
        session.run("CREATE CONSTRAINT trailer_id_unique IF NOT EXISTS FOR (t:Trailer) REQUIRE t.id IS UNIQUE");
        session.run("CREATE CONSTRAINT event_dedup_unique IF NOT EXISTS FOR (ce:ChangeEvent) REQUIRE ce.deduplicationKey IS UNIQUE");
        session.run("CREATE CONSTRAINT source_code_unique IF NOT EXISTS FOR (s:SourceDefinition) REQUIRE s.code IS UNIQUE");
        session.run("CREATE CONSTRAINT device_installation_id_unique IF NOT EXISTS FOR (d:DeviceInstallation) REQUIRE d.installationId IS UNIQUE");
        Log.debug("Constraints verified.");
    }

    private void createIndexes(Session session) {
        session.run("CREATE INDEX game_code_idx IF NOT EXISTS FOR (g:Game) ON (g.code)");
        session.run("CREATE INDEX edition_game_idx IF NOT EXISTS FOR (e:Edition) ON (e.gameCode)");
        session.run("CREATE INDEX trailer_game_idx IF NOT EXISTS FOR (t:Trailer) ON (t.gameCode)");
        session.run("CREATE INDEX event_game_idx IF NOT EXISTS FOR (ce:ChangeEvent) ON (ce.gameCode)");
        session.run("CREATE INDEX event_detected_idx IF NOT EXISTS FOR (ce:ChangeEvent) ON (ce.detectedAt)");
        Log.debug("Indexes verified.");
    }

    private void seedGame(Session session) {
        session.run("""
            MERGE (g:Game {code: 'GTA_VI'})
            SET g.name = 'Grand Theft Auto VI',
                g.releaseDate = date('2026-11-19'),
                g.officialSiteUrl = 'https://www.rockstargames.com/VI/',
                g.createdAt = coalesce(g.createdAt, datetime()),
                g.updatedAt = datetime()
            """);
        Log.debug("Game GTA_VI seeded.");
    }

    private void seedEditions(Session session) {
        session.run("""
            MERGE (e:Edition {id: 'ed-standard'})
            SET e.gameCode = 'GTA_VI',
                e.name = 'Standard Edition',
                e.normalizedType = 'STANDARD',
                e.official = true,
                e.status = 'PREORDER_AVAILABLE',
                e.description = 'The base GTA VI experience. Includes the Vintage Vice City Pack pre-order bonus and a free month of GTA+.',
                e.createdAt = coalesce(e.createdAt, datetime()),
                e.updatedAt = datetime()
            """);
        session.run("""
            MERGE (e:Edition {id: 'ed-ultimate'})
            SET e.gameCode = 'GTA_VI',
                e.name = 'Ultimate Edition',
                e.normalizedType = 'ULTIMATE',
                e.official = true,
                e.status = 'PREORDER_AVAILABLE',
                e.description = 'An exclusive collection of items threaded across all aspects of Jason and Lucia\\'s story. Includes the Vintage Vice City Pack, GTA+, and Ultimate Edition exclusives.',
                e.createdAt = coalesce(e.createdAt, datetime()),
                e.updatedAt = datetime()
            """);
        Log.debug("Editions seeded.");
    }

    private void seedRetailers(Session session) {
        // Retailer constraint
        session.run("CREATE CONSTRAINT retailer_code_unique IF NOT EXISTS FOR (r:Retailer) REQUIRE r.code IS UNIQUE");
        session.run("CREATE CONSTRAINT offer_id_unique IF NOT EXISTS FOR (o:RetailOffer) REQUIRE o.id IS UNIQUE");

        // PlayStation Store
        session.run("""
            MERGE (r:Retailer {code: 'PS_STORE'})
            SET r.name = 'PlayStation Store',
                r.countryCode = 'CH',
                r.officialStore = true,
                r.baseUrl = 'https://store.playstation.com/en-ch',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        // Xbox Store
        session.run("""
            MERGE (r:Retailer {code: 'XBOX_STORE'})
            SET r.name = 'Xbox Store',
                r.countryCode = 'CH',
                r.officialStore = true,
                r.baseUrl = 'https://www.xbox.com/en-ch',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        // Galaxus / Digitec
        session.run("""
            MERGE (r:Retailer {code: 'GALAXUS'})
            SET r.name = 'Galaxus',
                r.countryCode = 'CH',
                r.officialStore = false,
                r.baseUrl = 'https://www.galaxus.ch',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        // WOG
        session.run("""
            MERGE (r:Retailer {code: 'WOG'})
            SET r.name = 'WOG.ch',
                r.countryCode = 'CH',
                r.officialStore = false,
                r.baseUrl = 'https://www.wog.ch',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        // Rockstar Games Store (official store with pre-orders live)
        session.run("""
            MERGE (r:Retailer {code: 'ROCKSTAR_STORE'})
            SET r.name = 'Rockstar Games Store',
                r.countryCode = 'US',
                r.officialStore = true,
                r.baseUrl = 'https://www.rockstargames.com',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        // Amazon France
        session.run("""
            MERGE (r:Retailer {code: 'AMAZON_FR'})
            SET r.name = 'Amazon.fr',
                r.countryCode = 'FR',
                r.officialStore = false,
                r.baseUrl = 'https://www.amazon.fr',
                r.enabled = true,
                r.createdAt = coalesce(r.createdAt, datetime()),
                r.updatedAt = datetime()
            """);

        Log.debug("Retailers seeded.");
    }

    private void seedTrailers(Session session) {
        // Clean up old fake trailer URL, then re-seed with correct one
        session.run("MATCH (t:Trailer {id: 'trailer-2'}) SET t.videoUrl = 'https://www.youtube.com/watch?v=VQRLujxTm3c', t.thumbnailUrl = 'https://img.youtube.com/vi/VQRLujxTm3c/maxresdefault.jpg', t.updatedAt = datetime()");

        session.run("""
            MERGE (t:Trailer {id: 'trailer-1'})
            SET t.gameCode = 'GTA_VI',
                t.title = 'Grand Theft Auto VI Trailer 1',
                t.mediaType = 'TRAILER',
                t.official = true,
                t.publicationDate = datetime('2023-12-04T00:00:00Z'),
                t.videoUrl = 'https://www.youtube.com/watch?v=QdBZY2fkU-0',
                t.sourceUrl = 'https://www.rockstargames.com/VI/media/videos',
                t.thumbnailUrl = 'https://img.youtube.com/vi/QdBZY2fkU-0/maxresdefault.jpg',
                t.discoveredAt = coalesce(t.discoveredAt, datetime()),
                t.createdAt = coalesce(t.createdAt, datetime()),
                t.updatedAt = datetime()
            """);
        session.run("""
            MERGE (t:Trailer {id: 'trailer-2'})
            SET t.gameCode = 'GTA_VI',
                t.title = 'Grand Theft Auto VI Trailer 2',
                t.mediaType = 'TRAILER',
                t.official = true,
                t.publicationDate = datetime('2024-12-04T00:00:00Z'),
                t.videoUrl = 'https://www.youtube.com/watch?v=VQRLujxTm3c',
                t.sourceUrl = 'https://www.rockstargames.com/VI/media/videos',
                t.thumbnailUrl = 'https://img.youtube.com/vi/VQRLujxTm3c/maxresdefault.jpg',
                t.discoveredAt = coalesce(t.discoveredAt, datetime()),
                t.createdAt = coalesce(t.createdAt, datetime()),
                t.updatedAt = datetime()
            """);
        Log.debug("Trailers seeded.");
    }

    private void seedSourceDefinitions(Session session) {
        session.run("""
            MERGE (s:SourceDefinition {code: 'ROCKSTAR_MAIN'})
            SET s.name = 'Rockstar GTA VI Main Page',
                s.url = 'https://www.rockstargames.com/VI/',
                s.official = true,
                s.enabled = true,
                s.checkIntervalSeconds = 600,
                s.priority = 1,
                s.createdAt = coalesce(s.createdAt, datetime()),
                s.updatedAt = datetime()
            """);
        session.run("""
            MERGE (s:SourceDefinition {code: 'ROCKSTAR_EDITIONS'})
            SET s.name = 'Rockstar GTA VI Editions',
                s.url = 'https://www.rockstargames.com/VI/editions',
                s.official = true,
                s.enabled = true,
                s.checkIntervalSeconds = 600,
                s.priority = 1,
                s.createdAt = coalesce(s.createdAt, datetime()),
                s.updatedAt = datetime()
            """);
        session.run("""
            MERGE (s:SourceDefinition {code: 'ROCKSTAR_MEDIA'})
            SET s.name = 'Rockstar GTA VI Media',
                s.url = 'https://www.rockstargames.com/VI/media/videos',
                s.official = true,
                s.enabled = true,
                s.checkIntervalSeconds = 900,
                s.priority = 2,
                s.createdAt = coalesce(s.createdAt, datetime()),
                s.updatedAt = datetime()
            """);
        Log.debug("Source definitions seeded.");
    }

    private void seedInitialEvents(Session session) {
        // Only seed if no events exist
        var result = session.run("MATCH (ce:ChangeEvent) RETURN count(ce) AS cnt").single();
        if (result.get("cnt").asLong() > 0) return;

        session.run("""
            CREATE (ce:ChangeEvent {
                id: 'event-001',
                gameCode: 'GTA_VI',
                eventType: 'PREORDER_OPENED',
                priority: 'MAJOR',
                title: 'GTA VI pre-orders opened',
                description: 'Rockstar opened pre-orders for Grand Theft Auto VI on June 25, 2025.',
                evidenceUrl: 'https://www.rockstargames.com/newswire/article/5171972o3ak5oa/pre-order-grand-theft-auto-vi-on-june-25',
                deduplicationKey: 'PREORDER_OPENED:2025-06-25',
                detectedAt: datetime('2025-06-25T14:00:00Z'),
                userVisible: true,
                notificationEligible: true,
                createdAt: datetime()
            })
            """);
        session.run("""
            CREATE (ce:ChangeEvent {
                id: 'event-002',
                gameCode: 'GTA_VI',
                eventType: 'RELEASE_DATE_CHANGED',
                priority: 'CRITICAL',
                title: 'Release date confirmed: November 19, 2026',
                description: 'Rockstar confirmed the official release date.',
                oldValue: 'TBD',
                newValue: '2026-11-19',
                evidenceUrl: 'https://www.rockstargames.com/newswire/article/ak3ak31a49a221/grand-theft-auto-vi-is-now-set-to-launch-november-19-2026',
                deduplicationKey: 'RELEASE_DATE_CHANGED:TBD:2026-11-19',
                detectedAt: datetime('2025-11-06T14:00:00Z'),
                userVisible: true,
                notificationEligible: true,
                createdAt: datetime()
            })
            """);
        session.run("""
            CREATE (ce:ChangeEvent {
                id: 'event-003',
                gameCode: 'GTA_VI',
                eventType: 'NEW_TRAILER',
                priority: 'MAJOR',
                title: 'Trailer 2 released',
                description: 'Rockstar published the second official GTA VI trailer.',
                evidenceUrl: 'https://www.youtube.com/watch?v=VQRLujxTm3c',
                deduplicationKey: 'NEW_TRAILER:trailer-2',
                detectedAt: datetime('2024-12-04T00:00:00Z'),
                userVisible: true,
                notificationEligible: true,
                createdAt: datetime()
            })
            """);
        Log.debug("Initial events seeded.");
    }
}
