# GTA VI Waiting Room implementation roadmap

This roadmap separates correctness work from product expansion. Workflow A should ship first because notification trust is the product's foundation. Workflow B can then add retention and personalization without building on unreliable data.

## Workflow A: Stabilize, verify, and ship

Goal: every visible event and every push notification is relevant, traceable, deduplicated, and backed by a healthy source.

Scope status for this pull request:

- Implemented: every P0 item in A1 through A4, PWA installability, Java 26 CI, and the deployment image corrections.
- Pending deployment validation: the A5 production sequence and its two-cycle observation period.
- Planned follow-ups: deterministic non-AI adapters, extraction evidence, notification outbox, delivery metrics, overlapping-run protection, retention, and automated performance budgets.

### A1. Data correctness and safe extraction

| Priority | Work item | Implementation | Acceptance check |
|---|---|---|---|
| P0 | Treat AI output as a candidate | Run retailer results through `RetailerProductValidator` before snapshots, diffs, offers, or notifications | Music, books, apparel, GTA V, malformed URLs, and unsafe URLs never enter the user-facing pipeline |
| P0 | Repair known historical data | Correct Trailer 2 to May 6, 2025, correct pre-orders to June 25, 2026, and hide the known Amazon audio false positive with an idempotent startup migration | Existing and new databases show the same correct timeline |
| P0 | Make offer identity platform-aware | Identify offers by retailer, edition, and platform | PS5 and Xbox offers cannot overwrite one another |
| P1 | Add deterministic adapters before AI | Parse YouTube RSS as XML, Rockstar JSON-LD or embedded state, and retailer product JSON where available. Use AI only as a fallback | At least official sources have fixture-based parsers and do not require an LLM call |
| P1 | Store extraction evidence | Persist HTTP status, content hash, extractor version, candidate count, accepted count, rejection reasons, and a short evidence excerpt | An operator can explain why each event was created |

### A2. Diff and notification reliability

| Priority | Work item | Implementation | Acceptance check |
|---|---|---|---|
| P0 | Stop noisy listing alerts | Classify ordinary retailer listings as `RETAIL` and keep them out of push notifications | Only collector listings or user-selected price and stock transitions generate retail pushes |
| P0 | Detect meaningful retail changes | Compare price and availability for stable product identities | `PRICE_CHANGED`, `OUT_OF_STOCK`, and `BACK_IN_STOCK` events contain old value, new value, retailer name, and product evidence URL |
| P0 | Deduplicate before push | Send only when the Neo4j event merge created a new event | Repeating the same snapshot never sends a second push |
| P1 | Introduce a notification outbox | Write an `OutboxMessage` in the same transaction as the event, process it with retries, and add a unique constraint on event plus installation | Partial FCM failures retry safely and do not skip devices or resend to successful devices |
| P1 | Add delivery observability | Record queued, sent, failed, invalid-token, and retry states with latency counters | Delivery success and retry backlog are visible in metrics |

### A3. Scheduling, security, and health

| Priority | Work item | Implementation | Acceptance check |
|---|---|---|---|
| P0 | Honor source intervals | Select only monitors whose latest snapshot is older than their configured interval | A 10-minute scheduler trigger does not call 30-minute sources every time |
| P0 | Close the internal API bypass | Require the configured secret in every profile and compare it safely | Missing and wrong secrets return 401 in tests and production cannot start without a secret |
| P0 | Report real system health | Evaluate the latest result for all ten enabled sources and flag stale checks after two hours | One recent successful source cannot make a failing system look healthy |
| P1 | Prevent overlapping runs | Acquire a short Neo4j lease keyed by monitoring job before fetching | Two scheduler invocations cannot process the same sources concurrently |
| P1 | Add retention jobs | Keep full snapshots for 30 days, then retain daily hashes and all events | Neo4j storage stays bounded without losing audit history |

### A4. Frontend correctness, performance, and accessibility

| Priority | Work item | Implementation | Acceptance check |
|---|---|---|---|
| P0 | Fix retailer links and labels | Resolve legacy relative links server-side, reject unsafe links client-side, format currencies, and map internal retailer codes to names | Every visible offer opens the intended external retailer |
| P0 | Improve navigation and feedback | Offset sticky navigation, expose current section state, and show copy/share success or failure | Headings are visible after navigation and sharing always has feedback |
| P0 | Reduce largest contentful paint | Replace the initial YouTube iframe with a thumbnail facade, use explicit image dimensions, and use the optimized VI mark | Mobile no longer downloads the iframe until play is requested |
| P0 | Restore document semantics | Add one `h1`, `main`, `header`, `nav`, correct heading levels, labelled switches, and 44 px controls | Automated accessibility checks pass and keyboard navigation is complete |
| P1 | Complete installability | Ship a manifest, icons, install metadata, and a dedicated app-shell service worker without replacing the FCM worker | Chromium reports the site as installable and push still works after install |
| P1 | Re-run performance budgets | Set CI budgets for JS under 350 kB raw, LCP under 3 seconds on a representative mobile run, and no broken links | Pull requests fail when a budget regresses materially |

### A5. Delivery sequence

1. Run CI on Java 26 and Node 22.
2. Back up the production Neo4j database.
3. Deploy the backend first so seed corrections and URL normalization apply before the new frontend reads them.
4. Trigger one source at a time and inspect accepted products, rejected products, generated events, and notification eligibility.
5. Deploy the frontend and smoke test navigation, sharing, notification permission, offline reload, and every retailer link on mobile and desktop.
6. Observe two complete monitoring cycles before enabling FCM for all installations.

Exit criteria: zero known false positives, zero relative offer URLs, zero duplicate notifications in a replay test, ten-source health visible, frontend production build passing, backend test suite passing in CI.

## Workflow B: Add product value and retention features

Goal: turn the waiting room from a countdown page into the trusted control center for GTA VI availability, official updates, and launch planning.

Scope status: Workflow B is documented for future product work and is not implemented by this pull request.

### B1. Personal watch profile

Let users select country, preferred currency, platform, desired edition, maximum price, and retailers. Start without accounts by storing the profile against the existing installation ID. Add optional sign-in only when cross-device sync is needed.

Implementation:

- Add `WatchProfile` linked to `DeviceInstallation`.
- Add `PUT /api/v1/devices/{installationId}/watch-profile`.
- Filter offers and price notifications server-side using platform, country, edition, and price threshold.
- Add a short onboarding sheet after notification opt-in.

Success measure: at least 40 percent of notification-enabled installations complete a watch profile.

### B2. Offer comparison and price history

Show a sortable comparison table by edition and platform with total price, stock, retailer trust label, last verified time, and best current offer. Never label a retailer listing as official unless the source is official.

Implementation:

- Add immutable `OfferObservation` nodes for price and stock samples.
- Build `GET /api/v1/games/gta-vi/offers?platform=PS5&country=CH&edition=STANDARD`.
- Return current price, prior price, lowest recorded price, and verification timestamp.
- Add a compact 30-day price chart only when at least two real observations exist.

Success measure: offer click-through rate and price alert opt-in rate.

### B3. Verified update feed

Turn the timeline into a filterable feed with Official, Retail, Trailer, Release, Edition, Price, and Stock tabs. Each item should state the source, verified time, and what changed. Rumors should be excluded by default and, if added later, live in a clearly separate product area.

Implementation:

- Extend events with `sourceName`, `sourceOfficial`, `verificationState`, and structured evidence metadata.
- Add event filters and cursor pagination to the API.
- Add a detail drawer showing old value, new value, source link, and detection time.

Success measure: feed engagement without an increase in notification disable rate.

### B4. Notification center and controls

Give users confidence that push works and control over frequency.

Implementation:

- Add a Send test notification action with immediate result feedback.
- Add an in-app inbox backed by notification deliveries.
- Add quiet hours, instant versus daily digest, price threshold, and stock-only controls.
- Add deep links to the exact event or offer rather than only the home page.

Success measure: test notification success above 98 percent and lower opt-out rate.

### B5. Source status and transparency

Expose a public status page with each source's last check, last successful check, current state, typical interval, and recent error rate. Do not expose internal error payloads or credentials.

Implementation:

- Add a read model aggregated from `SourceSnapshot`.
- Add `/api/v1/status/sources` with safe public fields.
- Add stale, degraded, and operational states with clear recovery copy.

Success measure: support questions can be answered with a source status link.

### B6. Official sales tracker

Separate official shipment or sales disclosures from estimates. The app should never present analyst estimates as Rockstar-confirmed sales.

Implementation:

- Monitor Take-Two investor relations releases and filings as an official corporate source.
- Store `SalesMilestone` with metric type, quantity, period, source URL, official flag, and publication date.
- If estimates are later included, put them in a distinct Estimates view with publisher, methodology link, and confidence label.

Success measure: every displayed number has a primary source and an unambiguous official or estimate label.

### B7. Launch room

In the final month, shift the home page toward launch utility: platform-specific release time, preload status, install size when officially published, launch checklist, and spoiler controls for the update feed.

Implementation:

- Model release timestamps by region and platform rather than assuming local midnight once official times exist.
- Add official preload and patch observations as event types.
- Feature-flag the launch layout so it can be tested before activation.

Success measure: repeat visits during the final 30 days and low confusion around regional launch time.

### Suggested product order

1. Watch profile and test notification.
2. Offer comparison and price history.
3. Verified feed filters and source details.
4. Notification inbox, digest, and quiet hours.
5. Public source status.
6. Official sales tracker.
7. Launch room behind a feature flag.

Keep Workflow B on a separate product branch or project board from Workflow A. Workflow A blocks release when trust or reliability regresses. Workflow B is prioritized by user value and measured adoption.
