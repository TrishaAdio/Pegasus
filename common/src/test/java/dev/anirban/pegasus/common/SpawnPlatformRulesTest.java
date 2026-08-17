package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpawnPlatformRulesTest {
    private final SpawnPlatformRules.Settings settings = new SpawnPlatformRules.Settings(175, 128, 0.9);

    private static List<Boolean> platform(int matching, int other) {
        List<Boolean> blocks = new ArrayList<>(matching + other);
        blocks.addAll(Collections.nCopies(matching, Boolean.TRUE));
        blocks.addAll(Collections.nCopies(other, Boolean.FALSE));
        return blocks;
    }

    @Test
    void acceptsLargeHighAltitudeHayPlatform() {
        SpawnPlatformRules.Result result = SpawnPlatformRules.validate(180, platform(128, 0), settings);
        assertTrue(result.valid());
        assertEquals(128, result.matchingBlocks());
        assertEquals("valid", result.reason());
    }

    @Test
    void rejectsPlatformAtOrBelowMinimumY() {
        assertFalse(SpawnPlatformRules.validate(175, platform(200, 0), settings).valid());
        assertEquals("below-minimum-y", SpawnPlatformRules.validate(100, platform(200, 0), settings).reason());
    }

    @Test
    void rejectsPlatformThatIsTooSmall() {
        SpawnPlatformRules.Result result = SpawnPlatformRules.validate(200, platform(127, 0), settings);
        assertFalse(result.valid());
        assertEquals("not-enough-required-blocks", result.reason());
    }

    @Test
    void rejectsPlatformThatIsNotMostlyRequiredBlock() {
        // 130 hay of 200 total is only 65%, below the 90% ratio requirement.
        SpawnPlatformRules.Result result = SpawnPlatformRules.validate(200, platform(130, 70), settings);
        assertFalse(result.valid());
        assertEquals("platform-not-mostly-required-block", result.reason());
    }

    @Test
    void emptyScanIsRejectedRatherThanDividingByZero() {
        SpawnPlatformRules.Result result = SpawnPlatformRules.validate(200, List.of(), settings);
        assertFalse(result.valid());
        assertEquals(0, result.scannedBlocks());
    }

    @Test
    void settingsRejectNonsenseValues() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SpawnPlatformRules.Settings(175, 0, 0.9));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SpawnPlatformRules.Settings(175, 128, 1.5));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SpawnPlatformRules.Settings(9000, 128, 0.9));
    }
}
