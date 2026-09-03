# Technology Decisions

## Why Upstash Redis instead of Neo4j or PostgreSQL

The application uses Upstash Redis as its system of record through Quarkus's `quarkus-redis-client` extension.

**Reasons:**
1. **Deployment fit** — Upstash integrates with Vercel and is reachable securely from the Quarkus backend through a TLS Redis URL.
2. **Workload fit** — the app primarily reads and writes objects by stable identifiers and orders events/snapshots by time; JSON records, sets, and sorted sets cover those access patterns directly.
3. **One persistence service** — games, offers, monitoring state, devices, preferences, and delivery records share one namespaced store.
4. **Portable commands** — the implementation uses core Redis string, set, hash, and sorted-set commands rather than optional modules.

**Trade-offs:**
- Redis has no graph query planner; every supported lookup requires an explicit index key.
- Cross-key writes are not automatically relational, so idempotent writes and repairable indexes are used.
- Monitoring snapshots accumulate and require quota monitoring plus a future retention policy.
- Upstash eviction must remain disabled because the database is not merely a cache.

**Alternatives considered:**
- Neo4j: expressive graph queries, but more operational surface than this access pattern needs.
- PostgreSQL: strong relational constraints and ad-hoc queries, but requires a separate managed database and schema migration layer.

---

## Why AI-Powered Scraping (LangChain4j + Structured Output) instead of CSS Selectors

The original spec calls for Jsoup + per-site CSS selectors + fixture tests for every parser change.

**We use LangChain4j with strongly-typed DTOs instead** — the same pattern as Radar's `CompanySentimentAiService`.

### How it works

```
1. Fetch page HTML via Jsoup with bounded response size, retry, and timeout
2. Pass HTML to the typed @RegisterAiService (e.g. RetailerProductsExtractor)
3. LangChain4j automatically marshals the LLM's JSON response into a Java record
4. The record provides schema constraints
5. Deterministic validators reject irrelevant or unsafe candidates
6. Normalize + hash + diff as normal
```

### Typed extractors

Each source type has its own `@RegisterAiService` interface returning a Java record:

| Interface | Returns | Fields |
|-----------|---------|--------|
| `RockstarMainExtractor` | `RockstarMainData` | releaseDate, platforms, preorderAvailable |
| `RockstarEditionsExtractor` | `RockstarEditionsData` | editions[], hasCollectorEdition |
| `RockstarMediaExtractor` | `RockstarMediaData` | videos[] with type classification |
| `RetailerProductsExtractor` | `RetailerProductsData` | products[] with name, price, availability, URL |

### Why this is better

| Concern | CSS Selector Approach | AI Extraction Approach |
|---------|----------------------|----------------------|
| Site redesign | Parser breaks, needs emergency fix | AI adapts automatically |
| New source | Write new parser (hours) | Add URL + a typed AiService (minutes) |
| Unstructured data | Can't handle | AI extracts meaning from any HTML |
| Output validation | Manual JSON.parse + field checks | Java record — compiler-enforced |
| Retailer pages | Each needs custom selectors | Same pattern for all retailers |
| News articles | Need NLP anyway | Built-in understanding |

### Cost

At DeepSeek prices (~$0.27/M input tokens, ~$1.10/M output tokens):
- Each monitoring check: ~2-5K tokens input (HTML snippet) + ~200-500 tokens output (JSON)
- 10 sources × 6 checks/hour × 24h = ~1,440 checks/day worst case (~2,880,000 input tokens)
- Realistic: 10 sources × 2 checks/hour (most on 30-min intervals) = ~480 checks/day
- **Estimated cost: $0.05-0.30/day ($1.50-9/month)**

This is cheaper than developer time fixing broken selectors.

### Safety

- LLM extraction is a **tool**, not the truth authority
- Retailer products are validated after AI extraction for game identity, URL safety, enums, price, and availability
- Equivalent deterministic validators and fixture parsers for official sources are still planned
- If extraction fails → fallback to PARSER_FAILURE status
- Previous valid state is NEVER overwritten by a failed extraction
- The LLM cannot directly modify the database — it only produces structured JSON

---

## Cache and coordination

Upstash is currently the durable store, not a disposable cache. Future rate-limit leases, HTTP ETags, and monitoring-run locks can use separate keys with explicit TTLs. Durable entity and index keys do not receive TTLs.

---

## Why Vercel for Frontend

The frontend is a static React/Vite SPA. Vercel is:
- Free (100GB bandwidth)
- Already used for fawzz-tv-app
- Auto-deploys from Git
- Built-in preview deployments for PRs

**Alternatives considered:**
- Firebase Hosting: also free, but user already knows Vercel
- Cloud Run: overkill for a static SPA, costs money at scale

---

## Why Firebase Cloud Messaging (no alternative)

For native push notifications on Android/iOS, there is no viable alternative to FCM:
- APNs (iOS) requires Apple Developer account ($99/year)
- FCM wraps both APNs and Android push in one API
- Free tier is unlimited
- Firebase supports the current web-push PWA; Capacitor remains an option if native app packaging is added later

This is the ONE genuinely new service added beyond the user's existing stack, and it's unavoidable.

---

## Deployment Cost Summary

| Service | Free Tier | Est. MVP Usage | Cost |
|---------|-----------|---------------|------|
| Upstash Redis | Managed free tier | MVP records and snapshots | $0 while within current limits |
| Cloud Run Free | 2M req/month | ~10K req/month | $0 |
| Cloud Scheduler Free | 3 jobs | 1 job | $0 |
| Vercel Free | 100GB bandwidth | ~5GB | $0 |
| FCM Free | Unlimited | ~100s notifications | $0 |
| DeepSeek API | Pay-per-token | ~$5-10/month | ~$5-10 |
| **TOTAL** | | | **$5-10/month** |

---

## What We're NOT Using (from Original Spec)

| Spec Recommends | We Use Instead | Why |
|----------------|---------------|-----|
| PostgreSQL | Upstash Redis | Direct key/index access fits the current workload |
| Hibernate Panache | Quarkus Redis Data Source | No relational ORM is required |
| Flyway | Idempotent startup seeds + versioned key prefix | Redis has no relational schema |
| Jsoup CSS selectors | AI extraction | Adapts to site changes |
| Google Cloud SQL | Upstash Redis | Managed Redis and Vercel integration |
| Firebase project (hosting) | Vercel | Already using Vercel |
| Separate cache | Not currently used | Upstash already serves the current persistence access patterns |

