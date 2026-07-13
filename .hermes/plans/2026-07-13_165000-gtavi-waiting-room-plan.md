# GTA VI Waiting Room — Implementation Plan

> **For Hermes:** Use subagent-driven-development to implement this plan milestone by milestone, task by task. Each task is a fresh subagent with full context.

**Goal:** Build a production-ready mobile-first GTA VI release tracker with AI-powered monitoring, Neo4j backend, and push notifications.

**Architecture:** React/Vite/Capacitor frontend → Quarkus REST API → Neo4j AuraDB. AI-powered monitoring via LangChain4j + DeepSeek extracts structured data from any source page, eliminating fragile per-site parsers. Upstash-KV for caching. FCM for push.

**Tech Stack:** Java 26, Quarkus 3.36, Neo4j (quarkus-neo4j 6.7.0), LangChain4j, React 19, TypeScript, Tailwind CSS v4, Vite, Capacitor

---

## Milestone A: Read-Only App with Seeded Data

**Goal:** Display live countdown, trailers, editions from a Quarkus backend with Neo4j. No monitoring yet — seed data manually.

### Task A.1: Scaffold Quarkus backend project

**Objective:** Create the Quarkus project with all dependencies.

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/gtavi/`

**References:**
- Match radar-app's Quarkus 3.36.3 + Java 26 setup
- Dependencies: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-neo4j` (6.7.0), `quarkus-langchain4j`, `quarkus-arc`, `quarkus-smallrye-health`, `quarkus-micrometer`

**Step 1:** Use `mcp_quarkus_quarkus_create` or write pom.xml directly with radar-app conventions.
**Step 2:** Set `maven.compiler.release=26`, `--enable-preview` in jvm args.
**Step 3:** Add `.mvn/jvm.config` with `--enable-preview`.
**Step 4:** Verify `./mvnw quarkus:dev` starts successfully.
**Step 5:** Commit.

### Task A.2: Configure Neo4j and create initial schema

**Objective:** Set up Neo4j connection, create constraints and indexes, seed GTA_VI game node.

**Files:**
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/gtavi/config/Neo4jConfig.java`
- Create: `backend/src/main/java/com/gtavi/domain/Game.java`

**Step 1:** Add Neo4j config to application.properties:
```properties
quarkus.neo4j.uri=bolt://localhost:7687
quarkus.neo4j.authentication.username=neo4j
quarkus.neo4j.authentication.password=password
```
**Step 2:** Write a `@Startup` bean that creates constraints:
```cypher
CREATE CONSTRAINT game_code IF NOT EXISTS FOR (g:Game) REQUIRE g.code IS UNIQUE;
CREATE CONSTRAINT edition_id IF NOT EXISTS FOR (e:Edition) REQUIRE e.id IS UNIQUE;
CREATE CONSTRAINT retailer_code IF NOT EXISTS FOR (r:Retailer) REQUIRE r.code IS UNIQUE;
CREATE CONSTRAINT device_installation_id IF NOT EXISTS FOR (d:DeviceInstallation) REQUIRE d.installationId IS UNIQUE;
CREATE CONSTRAINT change_event_dedup IF NOT EXISTS FOR (ce:ChangeEvent) REQUIRE ce.deduplicationKey IS UNIQUE;
```
**Step 3:** Seed the GTA_VI Game node:
```cypher
MERGE (g:Game {code: 'GTA_VI'})
SET g.name = 'Grand Theft Auto VI',
    g.releaseDate = date('2026-11-19'),
    g.officialSiteUrl = 'https://www.rockstargames.com/VI/',
    g.createdAt = datetime()
```
**Step 4:** Seed editions and trailers from the spec.
**Step 5:** Write integration test verifying Game node exists.
**Step 6:** Commit.

### Task A.3: Create domain model (Neo4j nodes)

**Objective:** Define all domain entity classes as Neo4j nodes.

**Files:**
- Create: `backend/src/main/java/com/gtavi/domain/Game.java`
- Create: `backend/src/main/java/com/gtavi/domain/Edition.java`
- Create: `backend/src/main/java/com/gtavi/domain/EditionFeature.java`
- Create: `backend/src/main/java/com/gtavi/domain/Retailer.java`
- Create: `backend/src/main/java/com/gtavi/domain/RetailOffer.java`
- Create: `backend/src/main/java/com/gtavi/domain/Trailer.java`
- Create: `backend/src/main/java/com/gtavi/domain/NewsItem.java`
- Create: `backend/src/main/java/com/gtavi/domain/SourceDefinition.java`
- Create: `backend/src/main/java/com/gtavi/domain/SourceSnapshot.java`
- Create: `backend/src/main/java/com/gtavi/domain/MonitoringRun.java`
- Create: `backend/src/main/java/com/gtavi/domain/ChangeEvent.java`
- Create: `backend/src/main/java/com/gtavi/domain/DeviceInstallation.java`
- Create: `backend/src/main/java/com/gtavi/domain/NotificationPreference.java`
- Create: `backend/src/main/java/com/gtavi/domain/NotificationDelivery.java`

**Step 1:** Create each domain class as a simple POJO (no Lombok — use records where possible, manual getters/setters otherwise).
**Step 2:** Add JSON property annotations for serialization.
**Step 3:** Create a `Neo4jRepository` base class or service that wraps the Neo4j driver.
**Step 4:** Write test: create + read back each entity type.
**Step 5:** Commit.

### Task A.4: Create Neo4j service layer

**Objective:** Build service classes that execute Cypher queries against Neo4j.

**Files:**
- Create: `backend/src/main/java/com/gtavi/service/GameService.java`
- Create: `backend/src/main/java/com/gtavi/service/EditionService.java`
- Create: `backend/src/main/java/com/gtavi/service/TrailerService.java`
- Create: `backend/src/main/java/com/gtavi/service/EventService.java`

**Step 1:** Create `GameService` with methods:
```java
GameOverview getGameOverview(String code);
List<Trailer> getTrailers(String gameCode);
List<Edition> getEditions(String gameCode);
List<ChangeEvent> getEvents(String gameCode, int page, int size);
```
**Step 2:** Create `EditionService` with methods for querying editions and their offers.
**Step 3:** Create `TrailerService` with ordered-by-date queries.
**Step 4:** Create `EventService` with paginated event history.
**Step 5:** Write unit tests with Testcontainers Neo4j.
**Step 6:** Commit.

### Task A.5: Create REST API endpoints

**Objective:** Expose the game data through REST endpoints.

**Files:**
- Create: `backend/src/main/java/com/gtavi/api/publicapi/GameResource.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/GameOverviewResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/ReleaseInfoResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/TrailerResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/EditionResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/RetailOfferResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/ChangeEventResponse.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/SystemStatusResponse.java`

**Step 1:** Create DTO records (use Java records, not classes).
**Step 2:** Create `GameResource` with:
```java
@Path("/api/v1/games")
public class GameResource {
    @GET
    @Path("/gta-vi")
    public GameOverviewResponse getGameOverview();
    
    @GET
    @Path("/gta-vi/trailers")
    public List<TrailerResponse> getTrailers();
    
    @GET
    @Path("/gta-vi/editions")
    public List<EditionResponse> getEditions();
    
    @GET
    @Path("/gta-vi/events")
    public List<ChangeEventResponse> getEvents(@QueryParam("page") int page, @QueryParam("size") int size);
}
```
**Step 3:** Add CORS config for Vercel frontend.
**Step 4:** Add OpenAPI annotations.
**Step 5:** Write integration test: `@QuarkusTest` hitting each endpoint.
**Step 6:** Commit.

### Task A.6: Seed complete initial data

**Objective:** Populate Neo4j with all known GTA VI data from the spec baseline.

**Files:**
- Create: `backend/src/main/java/com/gtavi/config/DataSeeder.java`

**Step 1:** Create `@Startup` bean that runs on first start only (check if Game node exists).
**Step 2:** Seed 2 official editions (Standard, Ultimate) with features.
**Step 3:** Seed 2 trailers (Trailer 1, Trailer 2) with metadata.
**Step 4:** Seed source definitions for Rockstar pages.
**Step 5:** Verify all data accessible via API.
**Step 6:** Commit.

### Task A.7: Scaffold React frontend

**Objective:** Create the Vite + React + TypeScript frontend matching the radar-app stack.

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tailwind.config.ts` (or postcss config for TW v4)
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/index.css`

**Step 1:** Initialize with Vite React TS template.
**Step 2:** Add deps: `react-router-dom`, `tailwindcss` v4, `@tailwindcss/vite`, `react`, `react-dom`.
**Step 3:** Configure Tailwind with GTA VI color palette:
```css
@import "tailwindcss";

@theme {
  --color-bg-primary: #111117;
  --color-bg-card: #0C0D1B;
  --color-accent-pink: #FFB2C6;
  --color-accent-purple: #4B2F54;
  --color-accent-gold: #FFF9CB;
  --color-accent-orange: #FFD4A8;
  --color-accent-teal: #E6FFA3;
  --color-text-primary: #FFFFFF;
  --color-text-muted: #D9D1F6;
}
```
**Step 4:** Set up dark background, system font stack.
**Step 5:** Create basic routing with React Router.
**Step 6:** Verify `npm run dev` works on port 5173.
**Step 7:** Commit.

### Task A.8: Create API client and types

**Objective:** Build typed API client for the frontend.

**Files:**
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/game.ts`
- Create: `frontend/src/types/game.ts`

**Step 1:** Create TypeScript types matching backend DTOs:
```typescript
interface GameOverview {
  code: string;
  name: string;
  release: ReleaseInfo;
  latestTrailer: Trailer | null;
  trailers: Trailer[];
  editions: Edition[];
  latestEvents: ChangeEvent[];
  systemStatus: SystemStatus;
}
```
**Step 2:** Create API client with fetch wrapper.
**Step 3:** Create TanStack Query hooks: `useGameOverview()`, `useTrailers()`, `useEditions()`.
**Step 4:** Write a simple test verifying types.
**Step 5:** Commit.

### Task A.9: Build Countdown component

**Objective:** Create the hero countdown section — the core waiting room experience.

**Files:**
- Create: `frontend/src/features/countdown/Countdown.tsx`
- Create: `frontend/src/features/countdown/useCountdown.ts`
- Create: `frontend/src/features/countdown/CountdownSegment.tsx`

**Step 1:** Build `useCountdown` hook:
- Takes `releaseDate` (string) and `countdownPolicy`
- Calculates days/hours/minutes/seconds remaining
- Updates every second via `setInterval`
- Handles: negative duration → "OUT NOW", tab visibility, stale clock
**Step 2:** Build `CountdownSegment` for individual unit display (flip-card style or minimal).
**Step 3:** Build `Countdown` component with GTA VI styling.
**Step 4:** Show release date below countdown, verification badge, last check time.
**Step 5:** Write Vitest tests: normal countdown, release reached, stale data.
**Step 6:** Commit.

### Task A.10: Build Home page

**Objective:** Assemble the home screen with all sections.

**Files:**
- Create: `frontend/src/pages/HomePage.tsx`
- Create: `frontend/src/components/layout/AppLayout.tsx`
- Create: `frontend/src/components/ui/VerificationBadge.tsx`
- Create: `frontend/src/components/ui/StaleIndicator.tsx`

**Step 1:** Create `AppLayout` with header (GTA VI logo area), main content, footer.
**Step 2:** Create `VerificationBadge` showing "✓ Verified from official source" or "⚠ Last verified 8h ago".
**Step 3:** Assemble `HomePage`:
```
┌──────────────────────────────┐
│      GTA VI Logo             │
│   RELEASES IN                │
│   128D 04H 37M 12S           │
│   November 19, 2026          │
│   ✓ Verified · 4 min ago     │
├──────────────────────────────┤
│   LATEST TRAILER (hero card) │
├──────────────────────────────┤
│   EDITIONS                   │
│   [Standard] [Ultimate]      │
│   [Collector's — watch]      │
├──────────────────────────────┤
│   LATEST EVENTS              │
│   (timeline preview)         │
└──────────────────────────────┘
```
**Step 4:** Add loading skeleton states.
**Step 5:** Add error state with retry button.
**Step 6:** Commit.

### Task A.11: Build Trailer carousel

**Objective:** Horizontal swipe carousel for official trailers.

**Files:**
- Create: `frontend/src/features/trailers/TrailerCarousel.tsx`
- Create: `frontend/src/features/trailers/TrailerCard.tsx`
- Create: `frontend/src/features/trailers/LatestTrailerHero.tsx`

**Step 1:** Build `LatestTrailerHero` — large card showing the most recent trailer with thumbnail, title, date, "NEW" badge.
**Step 2:** Build `TrailerCard` for carousel items (smaller cards).
**Step 3:** Build `TrailerCarousel` with touch swipe support (implement simple touch handlers or use a lightweight lib).
**Step 4:** Add "Watch on Rockstar" external link.
**Step 5:** Write test: Trailer 2 appears before Trailer 1, "NEW" badge shows correctly.
**Step 6:** Commit.

### Task A.12: Build Editions section

**Objective:** Dynamic edition cards with Collector's Edition watch card.

**Files:**
- Create: `frontend/src/features/editions/EditionSection.tsx`
- Create: `frontend/src/features/editions/EditionCard.tsx`
- Create: `frontend/src/features/editions/CollectorWatchCard.tsx`

**Step 1:** Build `EditionCard` — displays edition name, type badge, status, features list, platform info.
**Step 2:** Build `CollectorWatchCard` — special card when no COLLECTOR edition exists:
```
┌──────────────────────────────────┐
│     COLLECTOR'S EDITION          │
│     Not announced yet            │
│                                  │
│  🔔 Get notified when Rockstar   │
│     announces it or pre-orders   │
│     open.                        │
│                                  │
│     [Enable alerts]              │
└──────────────────────────────────┘
```
**Step 3:** Create `EditionSection` — renders all editions dynamically from API data.
**Step 4:** Write test: 2 editions render, Collector placeholder when no collector exists, new edition doesn't break layout.
**Step 5:** Commit.

### Task A.13: Build Events timeline

**Objective:** Chronological timeline of important detected changes.

**Files:**
- Create: `frontend/src/features/events/EventTimeline.tsx`
- Create: `frontend/src/features/events/EventCard.tsx`

**Step 1:** Build `EventCard` with icon (by event type), title, description, date, source link.
**Step 2:** Build `EventTimeline` — vertical timeline, newest first, with priority-based styling.
**Step 3:** Add "View all events" page.
**Step 4:** Write test: ordering, event type icons, source links.
**Step 5:** Commit.

### Task A.14: Integrate Capacitor shell

**Objective:** Wrap the React app in Capacitor for native mobile deployment.

**Files:**
- Create: `frontend/capacitor.config.ts`
- Modify: `frontend/package.json` (add capacitor deps)

**Step 1:** Install `@capacitor/core`, `@capacitor/cli`, `@capacitor/android`, `@capacitor/ios`.
**Step 2:** Configure `capacitor.config.ts` with app ID `com.gtavi.waitingroom`.
**Step 3:** Add splash screen and app icon assets.
**Step 4:** Ensure `npm run build` produces valid Capacitor output.
**Step 5:** Commit.

### Milestone A Acceptance

- [x] Backend starts with `./mvnw quarkus:dev`
- [x] Neo4j has seeded game, editions, trailers
- [x] `GET /api/v1/games/gta-vi` returns full overview
- [x] Frontend starts with `npm run dev`
- [x] Countdown renders and updates every second
- [x] Trailer carousel shows Trailer 2 first
- [x] Editions render dynamically
- [x] Collector watch card shows when not announced
- [x] All tests pass (backend + frontend)

---

## Milestone B: AI-Powered Monitoring

**Goal:** AI-driven source monitoring that extracts structured data from any web page using LangChain4j + DeepSeek. Semantic diff engine detects meaningful changes.

### Task B.1: Configure LangChain4j with DeepSeek

**Objective:** Set up LangChain4j to use DeepSeek for HTML extraction.

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Create: `backend/src/main/java/com/gtavi/config/AiConfig.java`

**Step 1:** Add LangChain4j deps matching radar-app's setup.
**Step 2:** Configure DeepSeek API key in application.properties.
**Step 3:** Create `@ApplicationScoped` bean for `ChatLanguageModel`.
**Step 4:** Write test: send a simple extraction prompt, verify structured JSON response.
**Step 5:** Commit.

### Task B.2: Create monitoring interfaces

**Objective:** Build the core monitoring abstraction.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/core/GameSourceMonitor.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/core/MonitorResult.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/core/MonitorStatus.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/core/SourceRegistry.java`

**Step 1:** Create `GameSourceMonitor` interface:
```java
public interface GameSourceMonitor {
    SourceType sourceType();
    MonitorResult fetchCurrentState();
}
```
**Step 2:** Create `MonitorResult` record:
```java
public record MonitorResult(
    String sourceCode,
    String sourceUrl,
    Instant fetchedAt,
    MonitorStatus status,
    JsonNode normalizedData,
    String normalizedHash,
    String errorMessage
) {}
```
**Step 3:** Create `MonitorStatus` enum: `SUCCESS`, `NO_RELEVANT_DATA`, `TEMPORARY_FAILURE`, `PARSER_FAILURE`, `BLOCKED`, `RATE_LIMITED`, `UNKNOWN_FAILURE`.
**Step 4:** Create `SourceRegistry` that discovers all `GameSourceMonitor` beans.
**Step 5:** Commit.

### Task B.3: Create HTTP fetch utility

**Objective:** Build the web page fetcher with retry, timeout, ETag support.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/core/HttpFetcher.java`

**Step 1:** Create `HttpFetcher` class:
```java
public String fetch(String url, String userAgent, int timeoutMs, String etag);
```
**Step 2:** Implement retry with exponential backoff for 429, 5xx.
**Step 3:** Implement ETag/If-None-Match caching via Upstash-KV.
**Step 4:** Implement response size limits (prevent huge pages).
**Step 5:** Write test with WireMock.
**Step 6:** Commit.

### Task B.4: Create AI extraction service

**Objective:** The core innovation — use LLM to extract structured data from raw HTML.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/core/AiExtractionService.java`
- Create: `backend/src/main/resources/prompts/extract-gta-vi-data.txt`

**Step 1:** Create extraction prompt template:
```
You are a GTA VI data extraction agent. Given the HTML content of a Rockstar Games page,
extract structured information about Grand Theft Auto VI.

Return ONLY valid JSON. Do not include markdown code fences.

The JSON must follow this schema:
{
  "releaseDate": "YYYY-MM-DD" | null,
  "platforms": ["PLAYSTATION_5", "XBOX_SERIES_X_S"],
  "editions": [{"name": "...", "type": "STANDARD|ULTIMATE|COLLECTOR|..."}],
  "trailers": [{"title": "...", "url": "...", "publicationDate": "..."}],
  "preorderAvailable": true | false,
  ...
}

If a field is not found, use null or empty array.
```
**Step 2:** Create `AiExtractionService`:
```java
public JsonNode extractFromHtml(String html, String sourceType, String prompt);
```
**Step 3:** Add validation: verify JSON schema, required fields present, dates parseable.
**Step 4:** Write test with sample Rockstar HTML fixtures.
**Step 5:** Commit.

### Task B.5: Create normalization and hashing

**Objective:** Normalize extracted JSON and compute stable SHA-256 hash.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/core/Normalizer.java`
- Create: `backend/src/test/java/com/gtavi/monitoring/core/NormalizerTest.java`

**Step 1:** Create `Normalizer`:
```java
public String normalize(JsonNode data);
public String computeHash(String normalizedJson);
```
**Step 2:** Implement canonicalization: sort keys, sort arrays, normalize whitespace, normalize URLs, normalize edition/platform names.
**Step 3:** Write tests: identical data produces identical hash, different dates produce different hash, ordering doesn't matter.
**Step 4:** Commit.

### Task B.6: Create semantic diff engine

**Objective:** Compare two normalized states and produce semantic change events.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/diff/DiffEngine.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/diff/ReleaseDateDiff.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/diff/EditionDiff.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/diff/TrailerDiff.java`
- Create: `backend/src/main/java/com/gtavi/monitoring/diff/RetailerDiff.java`

**Step 1:** Create `DiffEngine` entry point:
```java
public List<ChangeEvent> diff(String sourceCode, JsonNode previous, JsonNode current);
```
**Step 2:** Implement `ReleaseDateDiff`: detects date changes → `RELEASE_DATE_CHANGED`.
**Step 3:** Implement `EditionDiff`: detects new/removed editions → `NEW_OFFICIAL_EDITION`, `EDITION_REMOVED`. Special detection for Collector → `COLLECTOR_EDITION_ANNOUNCED`.
**Step 4:** Implement `TrailerDiff`: detects new trailers → `NEW_TRAILER`.
**Step 5:** Implement `RetailerDiff`: detects availability changes → `PREORDER_OPENED`, `BACK_IN_STOCK`, `OUT_OF_STOCK`, `PRICE_CHANGED`.
**Step 6:** Write comprehensive unit tests for each diff type with fixture JSON.
**Step 7:** Commit.

### Task B.7: Create monitoring orchestrator

**Objective:** Orchestrate the full monitoring cycle.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/core/MonitoringOrchestrator.java`

**Step 1:** Create orchestrator:
```java
public MonitoringRun runCheck(Set<String> sourceCodes);
```
**Step 2:** Implement the flow: select due sources → for each: fetch → extract → normalize → hash → load previous → diff → create events → store snapshot → send notifications.
**Step 3:** Implement parser failure preservation: if extraction fails, keep last valid state, create `PARSER_FAILURE` event.
**Step 4:** Implement monitoring run persistence in Neo4j.
**Step 5:** Write integration test with WireMock simulating Rockstar pages.
**Step 6:** Commit.

### Task B.8: Create internal monitoring endpoint

**Objective:** Protected endpoint triggered by Cloud Scheduler.

**Files:**
- Create: `backend/src/main/java/com/gtavi/api/internal/MonitoringResource.java`
- Create: `backend/src/main/java/com/gtavi/api/internal/HealthResource.java`

**Step 1:** Create `MonitoringResource`:
```java
@Path("/internal/jobs")
public class MonitoringResource {
    @POST
    @Path("/check-updates")
    public MonitoringRunResponse checkUpdates(@QueryParam("source") String source);
    
    @GET
    @Path("/monitoring/status")
    public MonitoringStatusResponse getStatus();
}
```
**Step 2:** Add OIDC authentication (configurable for dev mode — allow shared secret).
**Step 3:** Add health endpoint with per-source status.
**Step 4:** Commit.

### Task B.9: Implement Rockstar main page monitor

**Objective:** First real monitor — extract release date from rockstargames.com/VI.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/rockstar/RockstarMainPageMonitor.java`
- Create: `backend/src/test/resources/fixtures/rockstar/main-page.html`

**Step 1:** Create `RockstarMainPageMonitor` implementing `GameSourceMonitor`.
**Step 2:** Implementation: fetch page → pass to AI extraction → get release date, platforms, preorder status.
**Step 3:** Save sanitized HTML fixture from real page.
**Step 4:** Write fixture test: extract release date from fixture, verify `2026-11-19`.
**Step 5:** Commit.

### Task B.10: Implement Rockstar editions monitor

**Objective:** Extract edition data from rockstargames.com/VI/editions.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/rockstar/RockstarEditionsMonitor.java`
- Create: `backend/src/test/resources/fixtures/rockstar/editions.html`

**Step 1:** Create `RockstarEditionsMonitor`.
**Step 2:** Extract Standard and Ultimate editions, features, pre-order links.
**Step 3:** Test: verify 2 editions detected, correct types.
**Step 4:** Test with modified fixture: add Collector edition → verify `COLLECTOR_EDITION_ANNOUNCED` event.
**Step 5:** Commit.

### Task B.11: Implement media/trailers monitor

**Objective:** Extract trailer data from rockstargames.com/VI/media/videos.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/rockstar/RockstarMediaMonitor.java`
- Create: `backend/src/test/resources/fixtures/rockstar/media.html`

**Step 1:** Create `RockstarMediaMonitor`.
**Step 2:** Extract all videos, classify: TRAILER, CHARACTER_CLIP, COVER_ART_ANIMATION, OTHER_VIDEO.
**Step 3:** Test: Trailer 2 and Trailer 1 detected, character clips classified correctly.
**Step 4:** Commit.

### Task B.12: Implement Newswire monitor

**Objective:** Detect new GTA VI articles from Rockstar Newswire.

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/rockstar/RockstarNewswireMonitor.java`

**Step 1:** Create `RockstarNewswireMonitor`.
**Step 2:** Detect GTA VI-specific articles, extract title, URL, date.
**Step 3:** Filter out non-GTA-VI content.
**Step 4:** Test with real + fixture data.
**Step 5:** Commit.

### Milestone B Acceptance

- [ ] Full monitoring cycle runs end-to-end with a test source
- [ ] AI extracts release date from real Rockstar page
- [ ] AI extracts editions from real editions page
- [ ] AI extracts trailers from real media page
- [ ] Semantic diff creates correct events
- [ ] Identical state produces no duplicate events
- [ ] Parser failure preserves last valid state
- [ ] Internal endpoint is protected

---

## Milestone C: Push Notifications

### Task C.1: Set up Firebase project

**Step 1:** Create Firebase project (or use existing from radar-app).
**Step 2:** Enable Cloud Messaging.
**Step 3:** Download service account JSON.
**Step 4:** Configure in application.properties.
**Step 5:** Commit (NOT the service account — use env var).

### Task C.2: Implement FCM sender

**Files:**
- Create: `backend/src/main/java/com/gtavi/notification/firebase/FcmSender.java`
- Create: `backend/src/main/java/com/gtavi/notification/template/NotificationTemplate.java`

**Step 1:** Create `FcmSender` using Firebase Admin SDK.
**Step 2:** Create notification templates for each event type.
**Step 3:** Handle token invalidation (mark inactive on FCM errors).
**Step 4:** Write test with mocked Firebase.
**Step 5:** Commit.

### Task C.3: Create device registration API

**Files:**
- Create: `backend/src/main/java/com/gtavi/api/publicapi/DeviceResource.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/DeviceRegistrationRequest.java`
- Create: `backend/src/main/java/com/gtavi/api/dto/PreferenceRequest.java`

**Step 1:** `POST /api/v1/devices` — register installation with FCM token.
**Step 2:** `PUT /api/v1/devices/{installationId}` — update token, last seen.
**Step 3:** `GET/PUT /api/v1/devices/{installationId}/preferences` — notification preferences.
**Step 4:** Write integration tests.
**Step 5:** Commit.

### Task C.4: Connect events to notifications

**Files:**
- Create: `backend/src/main/java/com/gtavi/notification/NotificationService.java`

**Step 1:** Create `NotificationService` that maps `ChangeEvent` → notification based on preferences.
**Step 2:** Implement deduplication: `(changeEventId, deviceInstallationId)` unique constraint.
**Step 3:** Implement priority-based delivery: CRITICAL always, MAJOR if enabled, etc.
**Step 4:** Write test: event delivery, preference filtering, dedup.
**Step 5:** Commit.

### Task C.5: Frontend push integration

**Files:**
- Create: `frontend/src/features/notifications/PushPermissionCard.tsx`
- Create: `frontend/src/features/notifications/NotificationSettings.tsx`
- Create: `frontend/src/features/notifications/usePushNotifications.ts`

**Step 1:** Build contextual permission request flow (not on first launch).
**Step 2:** Register FCM token with backend after permission granted.
**Step 3:** Build notification preferences UI with toggles.
**Step 4:** Handle foreground notifications.
**Step 5:** Test on real device.
**Step 6:** Commit.

### Milestone C Acceptance

- [ ] Device registration works
- [ ] FCM token stored and refreshable
- [ ] Test event sends push to device
- [ ] Preferences filter notifications correctly
- [ ] Duplicate events don't send duplicate pushes

---

## Milestone D: Retail Monitoring

### Task D.1: Create retailer monitor abstraction

**Files:**
- Create: `backend/src/main/java/com/gtavi/monitoring/retailer/RetailerMonitor.java`

**Step 1:** Similar to `GameSourceMonitor` but retailer-specific.
**Step 2:** Extract: edition name, platform, price, currency, availability, pre-order status.
**Step 3:** Write test with sample retailer HTML.
**Step 4:** Commit.

### Task D.2-D.4: Implement 3 retailer adapters

**Step 1:** PlayStation Store adapter.
**Step 2:** Xbox Store adapter.
**Step 3:** Galaxus/Digitec adapter (Switzerland-focused).
**Step 4:** Each with fixture tests.
**Step 5:** Commit after each.

### Milestone D Acceptance

- [ ] 3 order sources display in frontend
- [ ] Availability changes create semantic events
- [ ] Collector pre-order at retailer → critical event

---

## Milestone E: Production Hardening

### Task E.1: Docker and Cloud Run deployment

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/src/main/docker/Dockerfile.jvm`

### Task E.2: CI/CD pipelines

**Files:**
- Create: `.github/workflows/backend.yml`
- Create: `.github/workflows/frontend.yml`

### Task E.3: Offline cache and resilience

### Task E.4: Monitoring health dashboard

### Task E.5: Production deployment and smoke tests

---

## Testing Strategy

### Backend

```bash
# Unit tests
./mvnw test -Dtest="NormalizerTest,DiffEngineTest,AiExtractionServiceTest"

# Integration tests (Neo4j Testcontainers)
./mvnw verify -Dtest="GameResourceTest,DeviceResourceTest,MonitoringOrchestratorTest"

# Fixture-based parser tests
./mvnw test -Dtest="RockstarMainPageMonitorTest,RockstarEditionsMonitorTest"
```

### Frontend

```bash
# Unit + component tests
npm test

# E2E (optional, later milestone)
npx playwright test
```

---

## Edge Cases to Handle

1. **Release date disappears** → keep last valid, mark source failed
2. **Rockstar redesigns page** → AI adapts, no code change needed (key advantage)
3. **Edition renamed** → stable external key prevents false removal+creation
4. **Multiple Collector editions** → dynamic model supports any number
5. **Retailer pages return 404** → confirm multiple times before firing REMOVED event
6. **Price disappears** → set to null, don't zero
7. **Duplicate push after retry** → unique constraint (event + device)
8. **App offline** → render cached state with stale indicator
9. **LLM extraction fails** → fall back to CSS selectors if configured, or mark PARSER_FAILURE
