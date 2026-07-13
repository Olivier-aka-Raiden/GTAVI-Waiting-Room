package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NormalizerTest {

    private final Normalizer normalizer = new Normalizer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testIdenticalDataProducesSameHash() throws Exception {
        String json1 = "{\"name\":\"GTA VI\",\"date\":\"2026-11-19\"}";
        String json2 = "{\"date\":\"2026-11-19\",\"name\":\"GTA VI\"}";

        String hash1 = normalizer.computeHash(mapper.readTree(json1));
        String hash2 = normalizer.computeHash(mapper.readTree(json2));

        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Key order should not affect hash");
    }

    @Test
    void testDifferentDataProducesDifferentHash() throws Exception {
        String json1 = "{\"date\":\"2026-11-19\"}";
        String json2 = "{\"date\":\"2027-03-12\"}";

        String hash1 = normalizer.computeHash(mapper.readTree(json1));
        String hash2 = normalizer.computeHash(mapper.readTree(json2));

        assertNotEquals(hash1, hash2);
    }

    @Test
    void testNullInputReturnsNull() {
        assertNull(normalizer.computeHash(null));
    }

    @Test
    void testArrayOrderDoesNotAffectHash() throws Exception {
        String json1 = "{\"platforms\":[\"PS5\",\"XBOX\"]}";
        String json2 = "{\"platforms\":[\"XBOX\",\"PS5\"]}";

        String hash1 = normalizer.computeHash(mapper.readTree(json1));
        String hash2 = normalizer.computeHash(mapper.readTree(json2));

        assertEquals(hash1, hash2, "Array element order should not affect hash");
    }

    @Test
    void testNullValuesAreRemoved() throws Exception {
        String json = "{\"name\":\"GTA VI\",\"extra\":null}";

        var canonical = normalizer.canonicalize(mapper.readTree(json));

        assertFalse(canonical.has("extra"), "Null values should be removed");
        assertEquals("GTA VI", canonical.get("name").asText());
    }

    @Test
    void testSha256Is64Chars() {
        String hash = normalizer.sha256("hello");
        assertEquals(64, hash.length());
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash);
    }

    @Test
    void testCanonicalizeSortsKeys() throws Exception {
        String json = "{\"c\":3,\"a\":1,\"b\":2}";

        var canonical = normalizer.canonicalize(mapper.readTree(json));

        var fields = canonical.fieldNames();
        assertEquals("a", fields.next());
        assertEquals("b", fields.next());
        assertEquals("c", fields.next());
    }
}
