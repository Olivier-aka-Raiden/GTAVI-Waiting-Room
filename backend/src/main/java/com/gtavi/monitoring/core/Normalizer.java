package com.gtavi.monitoring.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Normalizes extracted JSON data and computes a stable SHA-256 hash.
 * Canonicalization ensures that semantically identical data produces the same hash
 * regardless of JSON key ordering, array ordering, or whitespace differences.
 */
@ApplicationScoped
public class Normalizer {

    private final ObjectMapper mapper;

    public Normalizer() {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /**
     * Normalize and compute SHA-256 hash of JSON data.
     *
     * @param data the raw extracted JSON
     * @return hex-encoded SHA-256 hash
     */
    public String computeHash(JsonNode data) {
        if (data == null) return null;
        try {
            JsonNode canonical = canonicalize(data);
            String json = mapper.writeValueAsString(canonical);
            return sha256(json);
        } catch (JsonProcessingException e) {
            Log.error("Failed to compute hash", e);
            return null;
        }
    }

    /**
     * Canonicalize a JSON node for deterministic comparison:
     * - Sort object keys alphabetically
     * - Sort arrays where order is not semantically meaningful
     * - Remove null values
     */
    public JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            // Sort keys alphabetically
            TreeSet<String> keys = new TreeSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (!value.isNull()) {
                    sorted.set(key, canonicalize(value));
                }
            }
            return sorted;
        }

        if (node.isArray()) {
            ArrayNode sorted = mapper.createArrayNode();
            // Sort array elements by their string representation
            TreeSet<String> elements = new TreeSet<>();
            for (JsonNode element : node) {
                elements.add(canonicalize(element).toString());
            }
            for (String element : elements) {
                try {
                    sorted.add(mapper.readTree(element));
                } catch (JsonProcessingException e) {
                    sorted.add(element);
                }
            }
            return sorted;
        }

        return node;
    }

    /**
     * Compute SHA-256 hex digest of a string.
     */
    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
