package com.gtavi.nativeimage;

import com.gtavi.api.dto.*;
import com.gtavi.domain.*;
import com.gtavi.monitoring.core.*;
import com.gtavi.news.AnnouncementExtraction;
import com.gtavi.service.GameService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeReflectionConfigTest {
    @Test
    void jsonBoundaryTypesAndTheirNestedTypesRetainJacksonMembers() {
        var registration = QuarkusFixNativeBuild.class.getAnnotation(RegisterForReflection.class);
        assertNotNull(registration);
        assertFalse(registration.serialization(), "Java serialization mode is not Jackson reflection");
        assertTrue(registration.methods());
        assertTrue(registration.fields());
        Set<Class<?>> targets = Set.of(registration.targets());
        var visited = new HashSet<Type>();
        for (Class<?> root : List.of(
                Game.class, Trailer.class, Edition.class, Retailer.class, RetailOffer.class,
                ChangeEvent.class, NotificationDelivery.class,
                GameOverviewResponse.class, SystemStatusResponse.class, ReleaseInfoResponse.class,
                TrailerResponse.class, EditionResponse.class, RetailOfferResponse.class,
                ChangeEventResponse.class, GameService.MonitoringHealth.class,
                RockstarMainData.class, RockstarMediaData.class, RockstarEditionsData.class,
                RetailerProductsData.class, AnnouncementExtraction.class)) {
            check(root, targets, visited);
        }
    }

    private static void check(Type type, Set<Class<?>> targets, Set<Type> visited) {
        if (!visited.add(type)) return;
        if (type instanceof ParameterizedType generic) {
            check(generic.getRawType(), targets, visited);
            for (Type argument : generic.getActualTypeArguments()) check(argument, targets, visited);
        } else if (type instanceof Class<?> concrete && concrete.getName().startsWith("com.gtavi.")) {
            assertTrue(targets.contains(concrete), () -> "Missing native registration: " + concrete.getName());
            if (concrete.isEnum()) return;
            for (Field field : concrete.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                    check(field.getGenericType(), targets, visited);
            }
        }
    }
}
