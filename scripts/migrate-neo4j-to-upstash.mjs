#!/usr/bin/env node

import { createHash } from "node:crypto";

const args = new Set(process.argv.slice(2));
const apply = args.has("--apply");
const prefix = process.env.GTAVI_REDIS_KEY_PREFIX || "gtavi:v1";
const database = process.env.NEO4J_DATABASE || "neo4j";
const labels = [
  "Game",
  "Trailer",
  "Edition",
  "Retailer",
  "RetailOffer",
  "SourceDefinition",
  "ChangeEvent",
  "SourceSnapshot",
  "DeviceInstallation",
  "NotificationDelivery",
];
const supportedLabels = new Set([...labels, "NotificationPreference"]);
const supportedRelationshipTypes = new Set(["HAS_PREFERENCES"]);

if (args.has("--help")) {
  console.log(`Usage: node scripts/migrate-neo4j-to-upstash.mjs [--apply]

Without --apply, the utility reads Neo4j and prints a migration plan only.
With --apply, it writes the idempotent plan through the Upstash REST pipeline.

Required Neo4j variables:
  NEO4J_HTTP_URL, NEO4J_USERNAME, NEO4J_PASSWORD

Required with --apply:
  UPSTASH_REDIS_REST_URL, UPSTASH_REDIS_REST_TOKEN

Optional:
  NEO4J_DATABASE (default: neo4j)
  GTAVI_REDIS_KEY_PREFIX (default: gtavi:v1)`);
  process.exit(0);
}

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

function neo4jBaseUrl() {
  if (process.env.NEO4J_HTTP_URL) {
    return process.env.NEO4J_HTTP_URL.replace(/\/$/, "");
  }
  const configured = required("NEO4J_URI");
  const parsed = new URL(configured);
  const secure = parsed.protocol.includes("+s") || parsed.protocol === "neo4j:";
  parsed.protocol = secure ? "https:" : "http:";
  if (!secure && parsed.port === "7687") parsed.port = "7474";
  else if (secure) parsed.port = "";
  parsed.username = "";
  parsed.password = "";
  parsed.pathname = "";
  return parsed.toString().replace(/\/$/, "");
}

async function runCypher(statement, parameters = {}) {
  const endpoint = `${neo4jBaseUrl()}/db/${encodeURIComponent(database)}/query/v2`;
  const username = required("NEO4J_USERNAME");
  const password = required("NEO4J_PASSWORD");
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`,
      "Content-Type": "application/json",
      Accept: "application/json",
      "Access-Mode": "READ",
    },
    body: JSON.stringify({ statement: statement.replace(/\s+/g, " ").trim(), parameters }),
  });
  const body = await response.json();
  if (!response.ok) {
    const detail = body.errors?.map((error) => error.message).join("; ") || response.statusText;
    throw new Error(`Neo4j Query API failed (${response.status}): ${detail}`);
  }
  if (body.errors?.length) {
    throw new Error(`Neo4j query failed: ${body.errors.map((error) => error.message).join("; ")}`);
  }
  return body.data?.values || [];
}

function key(...parts) {
  return `${prefix}:${parts.join(":")}`;
}

function timestamp(value) {
  if (!value) return 0;
  const parsed = Date.parse(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

function boolean(value, fallback) {
  if (value === undefined || value === null) return fallback;
  if (typeof value === "string") return value.toLowerCase() === "true";
  return Boolean(value);
}

function normalizeNeo4jValue(value) {
  if (Array.isArray(value)) return value.map(normalizeNeo4jValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value)
      .map(([field, nested]) => [field, normalizeNeo4jValue(nested)]));
  }
  if (typeof value === "string"
      && /^\d{4}-\d{2}-\d{2}T/.test(value)
      && /\[[^\]]+\]$/.test(value)) {
    return value.replace(/\[[^\]]+\]$/, "");
  }
  return value;
}

function stableId(type, value) {
  const digest = createHash("sha256")
    .update(JSON.stringify(value))
    .digest("hex")
    .slice(0, 24);
  return `${type}-${digest}`;
}

function addSet(commands, index, value) {
  if (value !== undefined && value !== null && value !== "") {
    commands.push(["SADD", index, String(value)]);
  }
}

function addSorted(commands, index, score, value) {
  if (value !== undefined && value !== null && value !== "") {
    commands.push(["ZADD", index, String(score), String(value)]);
  }
}

function addRecord(commands, type, id, value) {
  if (id === undefined || id === null || id === "") return;
  commands.push(["SET", key(type, String(id)), JSON.stringify(value)]);
}

function buildCommands(records, devicePreferences) {
  const commands = [];

  for (const game of records.Game) {
    addRecord(commands, "game", game.code, game);
  }
  for (const trailer of records.Trailer) {
    addRecord(commands, "trailer", trailer.id, trailer);
    addSorted(commands, key("trailers", "game", trailer.gameCode),
      timestamp(trailer.publicationDate), trailer.id);
  }
  for (const edition of records.Edition) {
    addRecord(commands, "edition", edition.id, edition);
    addSet(commands, key("editions", "game", edition.gameCode), edition.id);
  }
  for (const retailer of records.Retailer) {
    const id = retailer.code || retailer.id;
    if (!retailer.id && retailer.code) retailer.id = `retailer-${retailer.code.toLowerCase().replaceAll("_", "-")}`;
    addRecord(commands, "retailer", id, retailer);
    addSet(commands, key("retailers"), id);
  }
  for (const offer of records.RetailOffer) {
    offer.active = boolean(offer.active, true);
    offer.missedChecks ??= 0;
    addRecord(commands, "offer", offer.id, offer);
    addSet(commands, key("offers", "edition", offer.editionId), offer.id);
    addSet(commands, key("offers", "retailer", offer.retailerCode), offer.id);
  }
  for (const source of records.SourceDefinition) {
    addRecord(commands, "source", source.code, source);
    addSet(commands, key("sources"), source.code);
  }
  for (const event of records.ChangeEvent) {
    event.userVisible = boolean(event.userVisible, true);
    event.notificationEligible = boolean(event.notificationEligible, false);
    addRecord(commands, "event", event.id, event);
    if (event.deduplicationKey) {
      commands.push(["HSET", key("events", "dedup"), event.deduplicationKey, event.id]);
    }
    if (event.userVisible) {
      addSorted(commands, key("events", "game", event.gameCode, "visible"),
        timestamp(event.detectedAt), event.id);
    }
  }
  for (const snapshot of records.SourceSnapshot) {
    const id = snapshot.id || stableId("snapshot", {
      sourceCode: snapshot.sourceCode,
      checkedAt: snapshot.checkedAt,
      normalizedHash: snapshot.normalizedHash,
      status: snapshot.status,
    });
    snapshot.id = id;
    snapshot.successful = boolean(snapshot.successful, snapshot.status === "SUCCESS");
    addRecord(commands, "snapshot", id, snapshot);
    addSorted(commands, key("snapshots", "source", snapshot.sourceCode),
      timestamp(snapshot.checkedAt), id);
    if (snapshot.successful) {
      addSorted(commands, key("snapshots", "successful", snapshot.sourceCode),
        timestamp(snapshot.checkedAt), id);
    }
  }
  for (const device of records.DeviceInstallation) {
    addRecord(commands, "device", device.installationId, device);
    addSet(commands, key("devices"), device.installationId);
    if (device.pushToken) {
      commands.push(["HSET", key("device-tokens"), device.pushToken, device.installationId]);
    }
  }
  for (const [installationId, preferences] of devicePreferences) {
    if (preferences) addRecord(commands, "preferences", installationId, preferences);
  }
  for (const delivery of records.NotificationDelivery) {
    const id = delivery.id || stableId("delivery", {
      event: delivery.changeEventId,
      device: delivery.deviceInstallationId,
      message: delivery.providerMessageId,
      sentAt: delivery.sentAt,
    });
    delivery.id = id;
    addRecord(commands, "delivery", id, delivery);
    addSet(commands, key("deliveries"), id);
    addSet(commands, key("deliveries", "event", delivery.changeEventId), id);
  }
  return commands;
}

async function executeUpstash(commands) {
  const baseUrl = required("UPSTASH_REDIS_REST_URL").replace(/\/$/, "");
  const token = required("UPSTASH_REDIS_REST_TOKEN");
  const batchSize = 250;
  for (let offset = 0; offset < commands.length; offset += batchSize) {
    const batch = commands.slice(offset, offset + batchSize);
    const response = await fetch(`${baseUrl}/pipeline`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(batch),
    });
    if (!response.ok) {
      throw new Error(`Upstash pipeline failed (${response.status} ${response.statusText})`);
    }
    const results = await response.json();
    const errors = results.filter((result) => result.error);
    if (errors.length) {
      throw new Error(`Upstash rejected ${errors.length} command(s): ${errors[0].error}`);
    }
    console.log(`Applied ${Math.min(offset + batch.length, commands.length)}/${commands.length} commands`);
  }
}

async function verifyMigration(commands) {
  const checksByTarget = new Map();
  for (const command of commands) {
    if (command[0] === "SET") {
      checksByTarget.set(`key:${command[1]}`, {
        command: ["EXISTS", command[1]], valid: (result) => Number(result) === 1,
      });
    } else if (command[0] === "SADD") {
      checksByTarget.set(`set:${command[1]}:${command[2]}`, {
        command: ["SISMEMBER", command[1], command[2]], valid: (result) => Number(result) === 1,
      });
    } else if (command[0] === "ZADD") {
      checksByTarget.set(`zset:${command[1]}:${command[3]}`, {
        command: ["ZSCORE", command[1], command[3]], valid: (result) => result !== null,
      });
    } else if (command[0] === "HSET") {
      checksByTarget.set(`hash:${command[1]}:${command[2]}`, {
        command: ["HGET", command[1], command[2]], valid: (result) => result === command[3],
      });
    }
  }
  const checks = [...checksByTarget.values()];
  const baseUrl = required("UPSTASH_REDIS_REST_URL").replace(/\/$/, "");
  const token = required("UPSTASH_REDIS_REST_TOKEN");
  let verified = 0;
  for (let offset = 0; offset < checks.length; offset += 250) {
    const batch = checks.slice(offset, offset + 250);
    const response = await fetch(`${baseUrl}/pipeline`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(batch.map((check) => check.command)),
    });
    if (!response.ok) throw new Error(`Upstash verification failed (${response.status})`);
    const results = await response.json();
    for (let index = 0; index < batch.length; index++) {
      if (!results[index]?.error && batch[index].valid(results[index]?.result)) verified++;
    }
  }
  if (verified !== checks.length) {
    throw new Error(`Verification failed: ${verified}/${checks.length} records and indexes passed`);
  }
  console.log(`Verified ${verified} migrated records and index entries in Upstash.`);
}

async function main() {
  required("NEO4J_USERNAME");
  required("NEO4J_PASSWORD");
  const [labelRows, relationshipRows, orphanPreferenceRows] = await Promise.all([
    runCypher("MATCH (n) UNWIND labels(n) AS label RETURN DISTINCT label"),
    runCypher("MATCH ()-[r]->() RETURN DISTINCT type(r) AS relationshipType"),
    runCypher("MATCH (p:NotificationPreference) WHERE NOT (:DeviceInstallation)-[:HAS_PREFERENCES]->(p) RETURN count(p) AS orphanCount"),
  ]);
  const unexpectedLabels = labelRows.map(([label]) => label)
    .filter((label) => !supportedLabels.has(label));
  const unexpectedRelationships = relationshipRows.map(([type]) => type)
    .filter((type) => !supportedRelationshipTypes.has(type));
  const orphanPreferences = Number(orphanPreferenceRows[0]?.[0] || 0);
  if (unexpectedLabels.length || unexpectedRelationships.length || orphanPreferences) {
    const problems = [];
    if (unexpectedLabels.length) problems.push(`unsupported labels: ${unexpectedLabels.join(", ")}`);
    if (unexpectedRelationships.length) problems.push(`unsupported relationships: ${unexpectedRelationships.join(", ")}`);
    if (orphanPreferences) problems.push(`orphan notification preferences: ${orphanPreferences}`);
    throw new Error(`Refusing a partial migration (${problems.join("; ")})`);
  }
  const entries = await Promise.all(labels.map(async (label) => {
    const rows = await runCypher(`MATCH (n:\`${label}\`) RETURN properties(n) AS value`);
    return [label, rows.map(([value]) => normalizeNeo4jValue(value))];
  }));
  const records = Object.fromEntries(entries);
  const preferenceRows = await runCypher(`
    MATCH (d:DeviceInstallation)
    OPTIONAL MATCH (d)-[:HAS_PREFERENCES]->(p:NotificationPreference)
    RETURN d.installationId AS installationId, properties(p) AS preferences
  `);
  const devicePreferences = preferenceRows.map(([installationId, preferences]) =>
    [installationId, normalizeNeo4jValue(preferences)]);
  const commands = buildCommands(records, devicePreferences);

  console.log("Neo4j records found:");
  for (const label of labels) console.log(`  ${label}: ${records[label].length}`);
  console.log(`  NotificationPreference: ${devicePreferences.filter(([, value]) => value).length}`);
  console.log(`Redis commands planned: ${commands.length}`);

  if (!apply) {
    console.log("Dry run only. Re-run with --apply after reviewing these counts.");
    return;
  }
  await executeUpstash(commands);
  await verifyMigration(commands);
  console.log("Migration completed. Neo4j was not modified.");
}

main().catch((error) => {
  console.error(`Migration failed: ${error.message}`);
  process.exitCode = 1;
});

