package com.gtavi.nativeimage;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.RetailOffer;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeReflectionConfigTest {

    @Test
    void redisDomainTypesRetainMembersForJacksonInNativeImages() {
        RegisterForReflection registration = QuarkusFixNativeBuild.class
            .getAnnotation(RegisterForReflection.class);

        assertNotNull(registration);
        assertFalse(registration.serialization(),
            "Java serialization mode omits the reflective members Jackson needs");
        assertTrue(registration.methods());
        assertTrue(registration.fields());

        Set<Class<?>> targets = Set.of(registration.targets());
        assertTrue(targets.containsAll(Set.of(
            Game.class,
            Trailer.class,
            Edition.class,
            Retailer.class,
            RetailOffer.class,
            ChangeEvent.class
        )));
    }
}
