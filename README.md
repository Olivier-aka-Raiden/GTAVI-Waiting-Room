# GTA VI Waiting Room

> **Live countdown. Official trailers. Edition tracking. Collector's Edition watch. Push notifications when it matters.**

A mobile-first React + Capacitor app with a Quarkus/Neo4j backend that tracks official GTA VI release information and sends push notifications when meaningful changes occur.

---

## Stack (adapted from spec)

| Layer | Technology | Why |
|-------|-----------|-----|
| **Frontend** | React 19 + TypeScript + Vite + Tailwind CSS v4 | Matches radar-app stack; free Vercel hosting |
| **Mobile** | Capacitor | One codebase → web + Android + iOS; native push |
| **Backend** | Quarkus 3.36 + RESTEasy Reactive + LangChain4j | Matches radar-app; AI-powered scraping |
| **Database** | **Neo4j** (not PostgreSQL) | Existing expertise; graph model fits domain; free AuraDB tier |
| **Cache** | **Upstash-KV** | Rate limiting, snapshot cache; free tier |
| **Push** | Firebase Cloud Messaging | Only viable native push; generous free tier |
| **Frontend hosting** | **Vercel** | Free; existing fawzz-tv pattern |
| **Backend hosting** | Google Cloud Run | Free tier (2M req/month); existing radar-app pattern |
| **Scheduling** | Google Cloud Scheduler | Free tier; triggers internal monitoring endpoint |
| **CI/CD** | GitHub Actions | Free for public repos |

### Key changes from original spec

1. **Neo4j instead of PostgreSQL** — Your existing stack. The domain is naturally graph-shaped: Games → Editions → Retailers → Offers, Events linked to Sources, Devices with Preferences. Free AuraDB tier handles MVP scale.

2. **AI-powered scraping instead of CSS selectors** — The spec calls for Jsoup + per-site parsers. Instead, we use **LangChain4j + LLM** to extract structured data from any page. When Rockstar redesigns their site, the AI adapts automatically — no broken selectors, no emergency fixes. This is the core innovation: the monitoring system is an AI agent with tools.

3. **Upstash-KV** for caching snapshots, rate limiting, and device session state.

4. **Vercel** for frontend (matching fawzz-tv-app deployment).

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  React + Vite + Capacitor (Vercel)                      │
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
          ┌────────────┼────────────┐
          ▼            ▼            ▼
    ┌─────────┐ ┌──────────┐ ┌──────────┐
    │  Neo4j  │ │ Upstash  │ │ External │
    │ AuraDB  │ │   KV     │ │ Sources  │
    └─────────┘ └──────────┘ └──────────┘
```

### AI-Powered Monitoring Flow

```
Cloud Scheduler (every 5 min)
        │
        ▼
POST /internal/jobs/check-updates (OIDC-protected)
        │
        ▼
For each due source:
  1. Fetch HTML (Jsoup, with ETag/If-Modified-Since caching)
  2. Pass to LLM: "Extract structured data about GTA VI from this page"
  3. LLM returns validated JSON (release date, editions, trailers, etc.)
  4. Normalize + SHA-256 hash
  5. Load previous snapshot from Neo4j
  6. Compare → semantic diff → ChangeEvent
  7. If critical event → Firebase push to subscribed devices
  8. Store new snapshot in Neo4j + Upstash-KV cache
```

**Why AI scraping is better:**
- Adapts automatically when sites redesign
- No fragile CSS selectors to maintain
- Can extract semantic meaning from any HTML structure
- Works with new sources without code changes
- Costs ~$0.05-0.20/day in LLM tokens (DeepSeek)

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
├── frontend/                  # React + Vite + Capacitor
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

## Quick Start (after implementation)

```bash
# Backend
cd backend
./mvnw quarkus:dev

# Frontend
cd frontend
npm install
npm run dev

# Local Neo4j
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/password neo4j:5

# Local Upstash (or use remote free tier)
# Set UPSTASH_REDIS_URL and UPSTASH_REDIS_TOKEN in .env
```

---

## Free Tier Cost Analysis

| Service | Free Tier | Sufficient? |
|---------|-----------|-------------|
| **Neo4j AuraDB** | 50k nodes, 175k relationships | ✓ MVP |
| **Upstash KV** | 10k commands/day, 256MB | ✓ Caching |
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
| **A** | Read-only app: Quarkus + Neo4j + React + Countdown + Trailers + Editions | 🔴 Planned |
| **B** | AI monitoring: LLM-powered source extraction, semantic diff, events | 🔴 Planned |
| **C** | Push notifications: FCM, device registration, preferences, deep links | 🔴 Planned |
| **D** | Retail monitoring: AI-powered retailer scraping, availability tracking | 🔴 Planned |
| **E** | Production: CI/CD, Vercel deploy, Cloud Run deploy, monitoring | 🔴 Planned |

---

## License

MIT — see [LICENSE](./LICENSE)
