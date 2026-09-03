# Neo4j to Upstash Redis migration

This migration is designed for a zero-data-loss cutover:

- Neo4j remains unchanged and available for rollback.
- The import is idempotent and can be run more than once.
- Existing Upstash keys outside the configured `gtavi:v1` namespace are untouched.
- The backend is switched only after record counts and API responses have been checked.

## What is stored in Redis

Domain objects are JSON strings stored under the `gtavi:v1` prefix. Redis sets provide lookup indexes, while sorted sets order trailers, visible events, and monitoring snapshots by timestamp. The implementation uses ordinary Redis commands rather than RedisJSON, so it works with a standard Upstash Redis database.

The migration transfers these Neo4j labels:

- `Game`, `Trailer`, `Edition`, `Retailer`, and `RetailOffer`
- `SourceDefinition` and `SourceSnapshot`
- `ChangeEvent`
- `DeviceInstallation` and its related `NotificationPreference`
- `NotificationDelivery`

Before writing, the utility inspects the graph schema and refuses to continue if it finds an application label, relationship type, or orphan preference that is not covered by this mapping. This prevents an apparently successful but partial migration.

## 1. Prepare and back up

1. Keep the production backend on the Neo4j version while importing.
2. Take an AuraDB snapshot or export before the cutover.
3. In Upstash, make sure eviction is disabled. Eviction is useful for caches but inappropriate when Redis is the system of record.
4. Copy both kinds of Upstash credentials:
   - The TCP/TLS URL for the Quarkus backend, beginning with `rediss://`.
   - The REST URL and REST token for the one-time migration utility.

The real URL contains no backslash: use `rediss://`, not `rediss\://`. Copy it directly from the Upstash Connect dialog so any special characters are encoded correctly.

## 2. Set migration variables

Open PowerShell in the repository root. Use placeholders here; do not save real values in `.env.example` or commit them.

```powershell
$env:NEO4J_HTTP_URL = "https://your-aura-host.databases.neo4j.io"
$env:NEO4J_USERNAME = "neo4j"
$env:NEO4J_PASSWORD = "your-neo4j-password"
$env:NEO4J_DATABASE = "neo4j"

$env:UPSTASH_REDIS_REST_URL = "https://still-bullfrog-xxxxxx.upstash.io"
$env:UPSTASH_REDIS_REST_TOKEN = "your-rest-token"
$env:GTAVI_REDIS_KEY_PREFIX = "gtavi:v1"
```

`NEO4J_HTTP_URL` is the HTTPS database host, without `/db/...` at the end. For a local Neo4j instance it is normally `http://localhost:7474`. The utility uses Neo4j's Query API v2, which is available on Aura and current Neo4j 5/6 installations.

## 3. Dry-run, import, and verify

The first command reads Neo4j and prints label counts without writing to Upstash:

```powershell
node scripts/migrate-neo4j-to-upstash.mjs
```

Review the counts. Then perform the import:

```powershell
node scripts/migrate-neo4j-to-upstash.mjs --apply
```

The utility writes commands in small REST pipeline batches and verifies every migrated JSON record and index entry. A pipeline is not atomic, but every key is deterministic, so a failed or interrupted run can safely be repeated.

## 4. Configure the backend

Set the following environment variables on the service that runs the Quarkus backend:

```text
UPSTASH_REDIS_URL=rediss://default:<token>@still-bullfrog-xxxxxx.upstash.io:6379
GTAVI_REDIS_KEY_PREFIX=gtavi:v1
```

If the backend remains on Google Cloud Run, configure these variables in Cloud Run. Adding them only to the Vercel frontend does not expose them to Cloud Run. Never put the Redis URL in a `VITE_*` variable or client-side code.

The REST token is not used by the running Java backend. It is only needed by the migration utility.

## 5. Deploy and smoke-test

Deploy the Redis-backed backend with monitoring initially disabled. Check:

```text
GET /api/health
GET /api/v1/games/gta-vi
GET /api/v1/games/gta-vi/trailers
GET /api/v1/games/gta-vi/editions
GET /api/v1/games/gta-vi/events?page=0&size=20
```

Compare the game, trailer, edition, offer, and event counts with the migration dry-run. Test one device registration and preference update. Once those checks pass, enable monitoring and run one internal monitoring cycle.

## 6. Rollback and retirement

Do not delete the Neo4j database during the initial deployment. If verification fails, redeploy the previous backend revision with its `NEO4J_*` variables; the migration never changed Neo4j.

After the Redis-backed version has run successfully through several monitoring cycles and notification tests, take a final Neo4j export and retire the old credentials/database according to the desired retention period.

## Operational notes

- Use the writable/default Upstash credential, not a read-only token.
- Keep `GTAVI_REDIS_KEY_PREFIX` stable after migration. Changing it points the app at a different logical dataset.
- Monitor Upstash storage and command quotas, especially because source snapshots accumulate over time.
- Rotate the Upstash credentials immediately if a full connection URL or REST token is exposed in logs, source control, or client-side assets.

