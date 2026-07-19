# GTA VI Waiting Room

> **Live countdown. Official trailers. Edition tracking. Collector's Edition watch. Push notifications when it matters.**

A mobile-first React PWA with a Quarkus/Neo4j backend that tracks official GTA VI release information and sends web push notifications when meaningful changes occur.

---

## Stack (adapted from spec)

| Layer | Technology | Why |
|-------|-----------|-----|
| **Frontend** | React 19 + TypeScript + Vite + Tailwind CSS v4 | Matches radar-app stack; free Vercel hosting |
| **Mobile** | Installable PWA | Manifest, offline app shell, and Firebase web push. Capacitor is a future option |
| **Backend** | Quarkus 3.37 + REST + LangChain4j | Java 26 service with AI-assisted extraction |
| **Database** | **Neo4j** (not PostgreSQL) | Existing expertise; graph model fits domain; free AuraDB tier |
| **State and snapshots** | **Neo4j** | Current implementation. A separate cache is not implemented yet |
| **Push** | Firebase Cloud Messaging | Only viable native push; generous free tier |
| **Frontend hosting** | **Vercel** | Free; existing fawzz-tv pattern |
| **Backend hosting** | Google Cloud Run | Free tier (2M req/month); existing radar-app pattern |
| **Scheduling** | Google Cloud Scheduler | Free tier; triggers internal monitoring endpoint |
| **CI/CD** | GitHub Actions | Free for public repos |

### Key changes from original spec

1. **Neo4j instead of PostgreSQL** — Your existing stack. The domain is naturally graph-shaped: Games → Editions → Retailers → Offers, Events linked to Sources, Devices with Preferences. Free AuraDB tier handles MVP scale.

2. **AI-assisted extraction with deterministic validation** - LangChain4j proposes structured candidates from fetched pages. Business validation rejects irrelevant retailer products, invalid enums, and unsafe URLs before data reaches snapshots, diffs, offers, or notifications.

3. **Neo4j-backed snapshots and scheduling** - source snapshots, intervals, events, offers, devices, and preferences currently live in Neo4j. Rate limiting and conditional HTTP caching remain planned work.

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
           │  Neo4j  │               │ External │
           │ AuraDB  │               │ Sources  │
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
- Change events are merged by deduplication key before FCM delivery, so replaying the same source state does not resend the event.
- Every monitor honors its own interval even though Cloud Scheduler can trigger the orchestration endpoint every ten minutes.
- Monitoring health is based on the latest result from all ten enabled sources. A stale or failed source makes the public status degraded.
- Offers are separated by retailer, edition, and platform. Legacy relative URLs are resolved against the retailer domain, and offers missing from repeated checks become inactive.
- Disabling notifications updates the backend eligibility flag. Re-registering the same FCM token deactivates older installations to avoid duplicate delivery.
- The frontend is an installable PWA with an app-shell service worker. Firebase messaging uses a separate worker scope so push registration does not replace offline support.

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
│   │   ├── domain/            # Domain model (Neo4j nodes)
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

Requirements: Java 26, Maven or the included Maven wrapper, Node.js 22, npm, and Neo4j 5 or 6.

```bash
# Backend
cd backend
./mvnw quarkus:dev

# Frontend
cd frontend
npm ci
npm run dev

# Local Neo4j
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password neo4j:5

```

---

## Free Tier Cost Analysis

| Service | Free Tier | Sufficient? |
|---------|-----------|-------------|
| **Neo4j AuraDB** | 50k nodes, 175k relationships | ✓ MVP |
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
| **A** | Read-only app: Quarkus + Neo4j + React + Countdown + Trailers + Editions | ✅ Done |
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

When a retailer monitor detects GTA VI products (via AI extraction), they appear as "Where to order" links on the corresponding edition card — with price, currency, platform, and availability status. The app currently tracks **6 retailers** across Switzerland and France.

---

## License

MIT — see [LICENSE](./LICENSE)
