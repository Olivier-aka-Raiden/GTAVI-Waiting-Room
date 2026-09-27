# GTA VI Waiting Room

> **Live countdown. Official trailers. Edition tracking. Collector's Edition watch. Push notifications when it matters.**

A mobile-first React PWA with a Quarkus/Upstash Redis backend that tracks official GTA VI release information and sends web push notifications when meaningful changes occur.

---

## Stack (adapted from spec)

| Layer | Technology | Why |
|-------|-----------|-----|
| **Frontend** | React 19 + TypeScript + Vite + Tailwind CSS v4 | Matches radar-app stack; free Vercel hosting |
| **Mobile** | Installable PWA | Manifest, offline app shell, and Firebase web push. Capacitor is a future option |
| **Backend** | Quarkus 3.37 + REST + LangChain4j | Java 26 service with AI-assisted extraction |
| **Database** | **Upstash Redis** | Managed, TLS-secured Redis with a Vercel integration |
| **State and snapshots** | **Upstash Redis** | JSON records plus sets and sorted sets for durable indexes |
| **Push** | Firebase Cloud Messaging | Only viable native push; generous free tier |
| **Frontend hosting** | **Vercel** | Free; existing fawzz-tv pattern |
| **Backend hosting** | Google Cloud Run | Free tier (2M req/month); existing radar-app pattern |
| **Scheduling** | Google Cloud Scheduler | Free tier; triggers internal monitoring endpoint |
| **CI/CD** | GitHub Actions | Free for public repos |

### Key changes from original spec

1. **Upstash Redis persistence** — Games, editions, offers, events, snapshots, devices, preferences, and notification deliveries use namespaced JSON records. Sets and sorted sets provide deterministic lookups and chronological ordering.

2. **AI-assisted extraction with deterministic validation** - LangChain4j proposes structured candidates from fetched pages. Business validation rejects irrelevant retailer products, invalid enums, and unsafe URLs before data reaches snapshots, diffs, offers, or notifications.

3. **Redis-backed snapshots and scheduling** - source snapshots, intervals, events, offers, devices, and preferences live in Upstash. A daily authenticated maintenance job compacts full snapshots after 30 days. Rate limiting and conditional HTTP caching remain planned work.

4. **Vercel** for frontend (matching fawzz-tv-app deployment).

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  React + Vite PWA (Vercel)                              │
│  ┌─────────┐ ┌──────────┐ ┌────────┐ ┌──────────────┐ │
│  │Countdown│ │ Trailers │ │Editions│ │Notifications │ │
│  └─────────┘ └──────────┘ └────────┘ └──────────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │ REST/JSON
┌────────────────────▼────────────────────────────────────┐
│  Quarkus Backend (Cloud Run)                             │
│  ┌──────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ REST API │ │ AI Monitoring │ │ Firebase Push       │ │
│  │ /api/v1  │ │ LangChain4j  │ │ FCM Sender          │ │
│  └──────────┘ └──────┬───────┘ └──────────────────────┘ │
│                      │                                   │
│  ┌───────────────────▼──────────────────────────────┐   │
│  │  Source Monitor (AI Agent with Tools)             │   │
│  │  Fetch → LLM Extract → Normalize → Hash → Diff   │   │
│  └───────────────────┬──────────────────────────────┘   │
└──────────────────────┼──────────────────────────────────┘
                       │
                 ┌─────┴──────────────────┐
                 ▼                         ▼
           ┌─────────┐               ┌──────────┐
           │ Upstash │               │ External │
           │  Redis  │               │ Sources  │
           └─────────┘               └──────────┘
```

## AI Extraction: LangChain4j with Structured Output

The app uses **LangChain4j + DeepSeek** with typed DTOs for candidate extraction. Each source type has its own `@RegisterAiService` interface that returns a Java record, and retailer candidates pass through deterministic business validation before persistence or notification. Deterministic source adapters remain preferable where stable feeds or embedded structured data are available.

| Extractor | Returns | Used by |
|-----------|---------|---------|
| `RockstarMainExtractor` | `RockstarMainData` | Release date, platforms, pre-order state |
| `RockstarEditionsExtractor` | `RockstarEditionsData` | Edition list, Collector detection |
| `RockstarMediaExtractor` | `RockstarMediaData` | Trailers, video classification |
| `RetailerProductsExtractor` | `RetailerProductsData` | Product listings, price, availability |

### Why this is better than CSS selectors

| Concern | CSS Selector Approach | AI Extraction Approach |
|---------|----------------------|----------------------|
| Site redesign | Parser breaks, needs emergency fix | AI adapts automatically |
| New source | Write new parser (hours) | Add URL + a typed AiService (minutes) |
| Unstructured data | Can't handle | AI extracts meaning from any HTML |
| Output validation | Manual JSON.parse + field checks | Java schema plus deterministic business validation |
| Retailer pages | Each needs custom selectors | Same pattern for all retailers |

---

## GTA VI Theme

### Color Palette (from rockstargames.com/VI)

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-primary` | `#111117` | Main background |
| `--bg-card` | `#0C0D1B` | Cards, surfaces |
| `--accent-pink` | `#FFB2C6` | Primary accent (GTA VI signature) |
| `--accent-purple` | `#4B2F54` | Secondary accent |
| `--accent-gold` | `#FFF9CB` | Headings, highlights |
| `--accent-orange` | `#FFD4A8` | Warm accents |
| `--accent-teal` | `#E6FFA3` | Status indicators, badges |
| `--text-primary` | `#FFFFFF` | Body text |
| `--text-muted` | `#D9D1F6` | Secondary text |
| `--text-dark` | `#0C0D1B` | Text on light backgrounds |

### Assets (from Rockstar CDN)

- Hero background: `poster_full.jpg` (Lucia + Jason artwork)
- GTA VI logo: `logo_gta.png` + `logo_vi.png`
- Ultimate Edition: `ultimate_mobile_en_us.jpg`
- Trailer 2 thumb: `trailerMobile.jpg`
- Vice City aesthetic: neon pink + palm trees + art deco

---

## Current reliability behavior

- Retailer AI output is treated as candidate data. A deterministic validator rejects unrelated games, music, books, merchandise, invalid values, and unsafe URLs before snapshots, diffs, offers, or notifications are written.
- Ordinary retailer listings appear as `RETAIL` events without a major-news push. Collector listings remain critical. Price changes, out-of-stock changes, and back-in-stock changes follow the matching user preferences.
- Event deduplication, the event record, its visible-event index, and one uniquely keyed notification delivery per `(eventId, installationId)` are created atomically by Redis. Replaying the same source state creates neither another event nor another delivery.
- Notification deliveries move independently through `QUEUED`, `SENT`, `RETRY`, `INVALID_TOKEN`, and `DEAD`. A failed or invalid device never stops the rest of the batch; transient failures use exponential backoff, completed devices are not selected again, and an interrupted batch resumes from its remaining rows on the next scheduler run.
- Device/token ownership changes and offer/entity index moves use atomic Redis scripts, preventing records and their lookup indexes from diverging during concurrent writes or process interruption.
- Every monitor honors its own interval even though Cloud Scheduler can trigger the orchestration endpoint every ten minutes.
- Cloud Scheduler calls `POST /internal/jobs/cleanup-snapshots` once daily with the same `X-Internal-Secret` used by monitoring. Full snapshots are kept for 30 days; older records become daily hash summaries, while the latest success and failure per source remain available in full.
- Monitoring health is based on the latest result from all enabled sources. A stale or failed source makes the public status degraded.
- Offers are separated by retailer, edition, and platform. Legacy relative URLs are resolved against the retailer domain, and offers missing from repeated checks become inactive.
- Disabling notifications updates the backend eligibility flag. Re-registering the same FCM token deactivates older installations to avoid duplicate delivery.
- The frontend is an installable PWA with an app-shell service worker. Firebase messaging uses a separate worker scope so push registration does not replace offline support.

Outbox behavior is configurable with `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_RETRY_BASE_SECONDS`, `NOTIFICATION_RETRY_MAX_SECONDS`, `NOTIFICATION_OUTBOX_BATCH_SIZE`, and `NOTIFICATION_DELIVERY_LEASE_SECONDS`. The monitoring job drains due deliveries before and after source checks, including runs where no source is currently due.

The detailed delivery plan and the separate product-expansion workflow are documented in [docs/implementation-roadmap.md](./docs/implementation-roadmap.md).

---

## Project Structure

```
GTAVI-Waiting-Room/
├── README.md                  # This file
├── backend/                   # Quarkus backend
│   ├── pom.xml
│   ├── src/main/java/com/gtavi/
│   │   ├── api/               # REST endpoints
│   │   ├── domain/            # Domain model (Redis JSON records)
│   │   ├── persistence/       # Upstash Redis records and indexes
│   │   ├── monitoring/        # AI-powered source monitors
│   │   ├── notification/      # FCM push sender
│   │   └── config/            # Quarkus config
│   └── src/test/
├── frontend/                  # React + Vite installable PWA
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── features/          # Countdown, trailers, editions, events
│   │   ├── components/        # UI components
│   │   ├── api/               # Backend client
│   │   └── hooks/             # Custom hooks
│   └── public/
├── docs/                      # Specs, architecture decisions
└── .github/workflows/         # CI/CD
```

---

## Quick Start

Requirements: Java 26, Maven or the included Maven wrapper, Node.js 22, npm, and Redis 7 for local development.

```bash
# Backend
cd backend
./mvnw quarkus:dev

# Frontend
cd frontend
npm ci
npm run dev

# Local Redis
docker run -d --name gtavi-redis -p 6379:6379 redis:7.4-alpine

```

Production requires `UPSTASH_REDIS_URL`, copied exactly from Upstash's TCP/TLS connection dialog. The value starts with `rediss://`. Set it on the service running the Quarkus backend; secrets configured for the Vercel frontend are not automatically visible to Cloud Run.

Set `SNAPSHOT_RETENTION_DAYS` to control full-snapshot retention (default `30`). Both internal Cloud Scheduler endpoints require `X-Internal-Secret`: `POST /internal/jobs/check-updates` triggers monitoring and `POST /internal/jobs/cleanup-snapshots` performs retention cleanup. The deployment commands in [docs/deploy-gcp.sh](./docs/deploy-gcp.sh) configure the ten-minute monitoring job and daily cleanup job.

For the one-time Neo4j data transfer and production cutover, follow [docs/upstash-migration.md](./docs/upstash-migration.md).

### Running backend tests

The integration tests use the existing `quarkus-redis-client` extension's [Redis Dev Services](https://quarkus.io/guides/redis-dev-services). Start Docker Desktop with its Linux engine running before launching tests, including from an IDE. Quarkus creates a temporary Redis container and supplies its connection URL automatically; no Upstash credentials are needed.

From `backend` on Windows:

```powershell
docker info
.\mvnw.cmd test
```

On Linux/macOS, use `./mvnw test`. `docker info` must successfully report the server; having only the Docker client installed is insufficient. If Docker is stopped, Dev Services cannot supply a Redis host and Quarkus fails with `RedisDataSource` / `Bean is not active` before the tests run.

If you already run a dedicated local test Redis without Docker, configure its connection explicitly for the test profile:

```powershell
.\mvnw.cmd test '-D%test.quarkus.redis.hosts=redis://localhost:6379' '-D%test.quarkus.redis.tls.enabled=false'
```

The explicit host makes Dev Services skip container creation. The server must already be running, and tests write seed data, devices, events, and snapshots, so use a test instance. Setting `REDIS_URL` alone does not configure tests: that variable is used by the development and production profiles.

Integration tests use `@QuarkusTest` and injected application beans with a real Redis Dev Service. Redis-backed tests share one Quarkus runtime but reset and reseed their unique `gtavi:test:<quarkus.uuid>` namespace before each test, and clean it afterwards. Cleanup refuses non-test namespaces and never uses `FLUSHDB` or `FLUSHALL`; a resource lock prevents overlapping fixture resets. Do not override the test key prefix with a development or production namespace.

Notification tests replace only the FCM sender using Quarkus's built-in `QuarkusMock`, so CDI wiring, configured batch/retry limits, and Redis outbox behavior remain under test. The monitoring pipeline tests exercise the real monitor, normalization, validation, snapshot/event writes and offer persistence with controlled HTTP/AI boundaries. Extractor tests invoke the real generated AI services with a deterministic in-process model and assert rendered prompts and parsed DTOs. They do not accept arbitrary exceptions as success.

HTML parsing uses a checked-in synthetic Next.js RSC fixture instead of fetching Rockstar's live website. Pure parsing, hashing and validation tests remain lightweight JUnit tests where starting the application adds no meaningful coverage. No real website, DeepSeek or Firebase access is required by the test suite.

---

## Free Tier Cost Analysis

| Service | Free Tier | Sufficient? |
|---------|-----------|-------------|
| **Upstash Redis** | Managed Redis with a free tier | ✓ MVP; confirm current storage/command limits |
| **Cloud Run** | 2M req/month, 360K GB-sec | ✓ |
| **Cloud Scheduler** | 3 jobs free | ✓ |
| **FCM** | Unlimited | ✓ |
| **Vercel** | 100GB bandwidth, 6K build-min | ✓ |
| **DeepSeek API** | Pay-per-token (~$5-10/month for monitoring) | ✓ Low cost |

**Total: $0-10/month for MVP.** No credit card needed for most services to start.

---

## Milestones

| Milestone | What | Status |
|-----------|------|--------|
| **A** | Read-only app: Quarkus + Upstash Redis + React + Countdown + Trailers + Editions | ✅ Done |
| **B** | AI monitoring: LLM-powered source extraction, semantic diff, events | ✅ Done |
| **C** | Push notifications: FCM, device registration, preferences, deep links | ✅ Done |
| **D** | Retail monitoring: AI-powered retailer scraping, availability tracking | ✅ Done |
| **E** | Production: CI/CD, Vercel deploy, Cloud Run deploy, monitoring | 🔴 In progress |

### Monitored Sources

| Source | Type | Check Interval |
|--------|------|---------------:|
| Rockstar GTA VI Main Page | Official | 10 min |
| Rockstar GTA VI Editions | Official | 10 min |
| Rockstar GTA VI Media/Videos | Official | 15 min |
| Rockstar YouTube Channel | Official | 15 min |
| PlayStation Store (CH) | Official Store | 30 min |
| Xbox Store (CH) | Official Store | 30 min |
| Rockstar Games Store | Official Store | 30 min |
| Galaxus (CH) | Swiss Retailer | 15 min |
| WOG.ch (CH) | Swiss Retailer | 30 min |
| Amazon.fr (FR) | Retailer | 30 min |

### Retailers Shown on Edition Cards

When a retailer monitor detects GTA VI products (via AI extraction), they appear as "Where to order" links on the corresponding edition card — with price, currency, platform, and availability status. Listings whose title matches no distinct edition (regional boxes and market variants) belong to the Standard Edition instead of becoming a card of their own. The app currently tracks **6 retailers** across Switzerland and France.

---

## License

MIT — see [LICENSE](./LICENSE)

## Official news, collectibles, and music

The ROCKSTAR_NEWS monitor discovers linked official announcements and products, retains retries in Redis, and combines typed AI extraction with metadata/JSON-LD fallbacks. The app has dedicated Collectibles, Music, and Official news sections.

Read endpoints under /api/v1/games/gta-vi/news: the root is paginated news, /{id} is article detail, /products is paginated products, and /status is the last crawl summary. Notifications open /?news={id}.

Discovery checks the verified public Newswire API, its GTA VI archive cursor, and linked official pages. Article bodies are hydrated from the public API. Both new and older AI extractors resume overlapping chunks; verified JSON-LD products and music links remain available during AI outages. Explicit shared links merge related pages; later preorder, stock, price and material news changes create separate events.

Budgets: gtavi.news.pages-per-run (12), gtavi.news.ai-calls-per-run (10), gtavi.news.ai-characters-per-run (100000), and gtavi.monitoring.ai-calls-per-source (4). Also configurable: gtavi.news.enabled, gtavi.news.newswire-tag (666), gtavi.news.alert-lookback-days (14), gtavi.news.seed-urls, and gtavi.notifications.max-source-age-days (14). Existing scheduling and dependencies are reused.

The status endpoint reports persistent pending/failed page counts and AI usage. Incomplete work degrades monitoring health and automatically retries; these diagnostics add no user alert stream. Cards retain separate variant/currency/market offers and verified values during partial extraction.

Run the frontend regression suite with `node --test frontend/scripts/news.test.mjs`; build with `npm --prefix frontend run build`. Run backend tests through the project's Quarkus Dev MCP workflow described in backend/AGENTS.md.

See [implementation report, verification, and rollout](docs/2026-09-26-news-reliability-implementation.md), including coordinated frontend/backend rollout requirements. Relevant Quarkus guides: [REST](https://quarkus.io/guides/rest) and [Redis](https://quarkus.io/guides/redis-reference).


### Extraction progress and recovery

AI extraction requests are stateless. Prepared input retains text, purchase/image evidence and embedded data while removing decorative markup. Budget exhaustion is reported as EXTRACTION_PENDING, with pendingSources in the authenticated monitoring response; it does not count as a parser failure or replace successful app data. Pending sources resume automatically. Existing gtavi.monitoring.ai-calls-per-source defaults to four. No migration or new environment variables are required.

See the [extraction reliability correction](docs/2026-09-26-extraction-reliability-fix.md) for the incident, behavior and regression coverage.

### Native JSON and crawl safety

Application JSON types and nested announcement records are registered centrally in QuarkusFixNativeBuild. Regression checks cover nested DTO registration and notification JSON round trips. Asset URLs remain card evidence but are excluded from page crawling; existing invalid queue entries are retired automatically in bounded batches. The news status includes skipped/skippedPages diagnostics without additional user notifications.

See the [crawler and native-readiness audit](docs/2026-09-26-crawler-native-readiness.md), including the native executable verification limitation and rollout guidance.

### Product and offer identity

Retailer URLs use stable identities so encoded URL variants cannot create duplicate offers. Missing prices preserve the last observed value in the same currency. The Album has one card with separate format choices; genuine retailer variants remain available in expandable groups. Existing records are reconciled automatically in public responses, with original source evidence retained. See the [duplicate-offer incident and rollout](docs/2026-09-26-duplicate-offers-fix.md).

### Android notification icon

The service worker uses assets/notification-badge-96.png for the small Android status-bar badge: a monochrome VI silhouette on transparent pixels. The colored icon-192.png remains the notification image and app icon. The badge was derived from logo-vi.png using the built-in image editor, with the prompt to keep only solid VI silhouettes on a transparent background; its alpha was preserved when resized to 96 pixels. Rebuild and deploy the frontend, then open the app so its service worker updates. Existing notifications retain their old icon; newly received notifications use the updated badge.

### AI platform normalization

The editions, main-page and retailer extractors share an explicit system-prompt contract for platform labels. Cached or noncanonical labels are normalized consistently, while an edition with unrecognized platform details retains its general official link. Unusable Rockstar observations preserve previous offers and retry instead of reporting an empty store. See the [platform-contract correction and regression coverage](docs/2026-09-26-rockstar-platform-contract.md).

### Extraction prompt contracts

All six extractors share explicit evidence and unknown-value rules. Prompts match their DTO fields, platform/edition/media vocabularies, URL handling and downstream validation. Unknown preorder status cannot close an offer; exact product evidence supports locale-formatted prices without mixing listings. Prompt/DTO contracts are regression-tested through generated AI service requests. See the [full prompt and backend contract audit](docs/2026-09-26-prompt-contract-audit.md).

### Album cards and news browsing

Music formats from the official Album announcement are reconciled into one card, including older short-label records. Official news appears newest first in a swipeable, keyboard-accessible card reader, and the section navigation follows scrolling and asynchronous layout changes. See [catalog, news and navigation notes](docs/2026-09-26-catalog-news-navigation.md) for regression coverage and deployment details.

### Newswire date recovery

Newswire responses with `errors: null` or `errors: []` are accepted. Verified dates enrich existing articles and cached extractions automatically; dated announcements lead the feed, with explicitly related undated landing pages reconciled out of the news view. See [date recovery and validation](docs/2026-09-26-newswire-date-recovery.md).

### Alert deduplication, edition grouping and prices

News alerts key their occurrence on the new fact set instead of a per-observation counter, carry the verified publication date, and are only pushed once per item per day; items published before `gtavi.notifications.max-source-age-days` (default 14) stay in the timeline without a push. Retailer listings that match no known edition join the base game's Standard Edition, and non-official `UNKNOWN` editions without offers are hidden from the public edition list. A price observed without a currency is quoted in the retailer's market currency (Amazon.fr EUR, Rockstar Store USD, Swiss stores CHF) and locale-formatted prices are normalized. See [duplicate alerts, stray editions and missing prices](docs/2026-09-27-alert-dedupe-editions-prices.md).

### Collection boxes and priced offers

Collectibles are reconciled per item: a collection box is one card, its contents are described by that card instead of becoming individual products, and an unpriced observation of a listing never renders beside its priced offer. Raw observations are retained, so existing records are corrected on read without a migration. The announcement prompt reports an included item separately only when the source gives it its own purchase URL. See [collection boxes, contents and duplicate offers](docs/2026-09-27-collectible-box-grouping.md).

### Album formats

Album formats named in prose ("CD jewel case", "liquid-filled vinyl", "splatter-edition vinyl") are folded into the album card as format choices instead of becoming separate Music cards. The album's own store host family counts as album evidence, format names are recognized by their words, and the release description is pinned to the album record. See [album formats appearing as their own card](docs/2026-09-27-album-format-card-fix.md).
