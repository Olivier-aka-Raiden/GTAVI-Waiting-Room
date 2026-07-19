# Technology Decisions

## Why Neo4j instead of PostgreSQL

The original spec calls for PostgreSQL + Hibernate ORM Panache + Flyway. We use Neo4j instead.

**Reasons:**
1. **Existing expertise** — radar-app already uses Neo4j (quarkus-neo4j 6.7.0), so there's zero learning curve.
2. **Domain fits graph model** — Games → Editions → Retailers → Offers, Events → Sources, Devices → Preferences. These are all connected entities best expressed as a graph.
3. **Free tier sufficient** — Neo4j AuraDB free: 50K nodes, 175K relationships. MVP needs ~100-500 nodes.
4. **JSON support** — Neo4j nodes can store JSON properties (snapshots, normalized data).

**Trade-offs:**
- No Flyway migrations → use `@Startup` init scripts + constraints
- No Panache repositories → use direct Cypher via Neo4j driver
- Less natural for "rows and columns" queries → but the app is relationship-heavy

**Alternatives considered:**
- PostgreSQL: spec default, but adds a new DB to manage
- MongoDB: document model is nice for snapshots but loses relationships

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

## Planned cache layer

A separate cache is not currently implemented. Neo4j stores source snapshots and scheduling state. A future cache could provide:
1. **Snapshot cache** — latest normalized state per source, for fast hash comparison
2. **Rate limiting** — prevent duplicate monitoring runs
3. **HTTP ETag cache** — store ETags per source URL for conditional requests

Upstash Redis is one candidate, but it should only be added after measuring Neo4j latency and monitoring overlap in production.

**Alternatives considered:**
- Redis on Cloud Run: no free tier, adds cost
- In-memory cache: lost on scale-to-zero, doesn't survive restarts
- Neo4j for caching: works but adds latency for simple key-value lookups

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
| Neo4j AuraDB Free | 50K nodes | ~200 nodes | $0 |
| Optional Upstash Redis | Not currently used | Add only if metrics justify it | $0 at MVP limits |
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
| PostgreSQL | Neo4j | Existing stack, graph model |
| Hibernate Panache | Neo4j driver + Cypher | No JPA for Neo4j |
| Flyway | @Startup init scripts | Neo4j has no Flyway |
| Jsoup CSS selectors | AI extraction | Adapts to site changes |
| Google Cloud SQL | Neo4j AuraDB | Free tier |
| Firebase project (hosting) | Vercel | Already using Vercel |
| Redis | Not currently used | Neo4j is sufficient for the present workload |
