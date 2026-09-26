package com.gtavi.monitoring.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlatformNamesTest {
    @Test void normalizesObservedNamesPunctuationAndTrademarks() {
        for (String label : List.of("PS5", "PlayStation 5", "PlayStation®5", "PlayStation™ 5", "Sony PlayStation 5", "PlayStation 5 (PS5)"))
            assertEquals("PS5", PlatformNames.normalize(label), label);
        for (String label : List.of("XSX", "Xbox Series X", "Xbox Series S", "Xbox Series X|S", "Xbox Series X / S", "Xbox Series X and Xbox Series S", "xboxsx"))
            assertEquals("XSX", PlatformNames.normalize(label), label);
        assertEquals("PC", PlatformNames.normalize("Windows PC"));
        assertEquals("UNKNOWN", PlatformNames.normalize("unknown"));
    }
    @Test void neverGuessesUnrecognizedOrFutureHardware() {
        for (String label : List.of("", " ", "PlayStation 4", "PS50", "Xbox One", "Nintendo Switch", "PS5, Xbox Series X"))
            assertNull(PlatformNames.normalize(label), label);
        assertNull(PlatformNames.normalize(null));
    }
}
