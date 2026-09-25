package com.gtavi.config;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.ArrayList;

/** Shared Redis Dev Service, isolated fixtures for every integration test. */
@ResourceLock("gtavi-test-redis")
public abstract class RedisBackedTest {

    @Inject
    RedisDataSource redis;

    @Inject
    RedisDataInitializer initializer;

    @ConfigProperty(name = "gtavi.redis.key-prefix")
    String prefix;

    @BeforeEach
    protected void resetRedisFixtures() {
        clearRedisFixtures();
        initializer.onStart(new StartupEvent());
    }

    @AfterEach
    protected void clearRedisFixtures() {
        // Never FLUSHDB/FLUSHALL: an explicitly configured test server may contain
        // unrelated data. Refuse broad or non-test namespaces before deleting anything.
        if (!prefix.matches("gtavi:test:[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new IllegalStateException("Redis test cleanup requires a unique gtavi:test:<UUID> namespace");
        }
        var keys = new ArrayList<String>();
        redis.key().scan(new KeyScanArgs().match(prefix + ":*")).toIterable().forEach(keys::add);
        if (!keys.isEmpty()) {
            redis.key().del(keys.toArray(String[]::new));
        }
    }
}
