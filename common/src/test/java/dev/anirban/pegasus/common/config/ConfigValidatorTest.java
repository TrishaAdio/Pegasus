package dev.anirban.pegasus.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.anirban.pegasus.common.PegasusConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {
    @Test
    void emptyConfigYieldsDocumentedDefaults() {
        ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(Map.of()));
        PegasusConfig config = result.config();
        assertFalse(result.hasWarnings());
        assertEquals(175, config.naturalSpawning().platform().minimumY());
        assertEquals(128, config.naturalSpawning().platform().minimumPlatformBlocks());
        assertEquals("HAY_BLOCK", config.naturalSpawning().requiredBlock());
        assertEquals(60, config.naturalSpawning().checkInterval().toSeconds());
        assertEquals(10, config.naturalSpawning().spawnChancePercent());
        assertEquals(1, config.naturalSpawning().maxNearbyPegasus());
        assertEquals(List.of("world"), config.naturalSpawning().allowedWorlds());
        assertEquals(100, config.breeding().bothPreparedChance());
        assertEquals(50, config.breeding().onePreparedChance());
        assertEquals("NETHER_STAR", config.breedingItems().preparationItem());
        assertEquals("GOLDEN_CARROT", config.breedingItems().triggerItem());
        assertEquals(30.0, config.entity().maxHealth());
        assertFalse(config.debug());
    }

    @Test
    void outOfRangeValuesAreClampedAndReportedNotThrown() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("taming.chance-percent", 5000);
        raw.put("natural-spawning.spawn-chance-percent", -20);
        raw.put("entity.max-health", 0);
        raw.put("flight.sprint-multiplier", 0.1);

        ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(raw));
        assertEquals(100, result.config().taming().chancePercent());
        assertEquals(0, result.config().naturalSpawning().spawnChancePercent());
        assertEquals(1.0, result.config().entity().maxHealth());
        assertEquals(1.0, result.config().flight().sprintMultiplier());
        assertEquals(4, result.warnings().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("taming.chance-percent")));
    }

    @Test
    void nonNumericAndNonFiniteValuesFallBackSafely() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("entity.max-health", "very healthy");
        raw.put("flight.horizontal-speed", Double.NaN);
        raw.put("natural-spawning.check-interval-seconds", "soon");

        ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(raw));
        assertEquals(30.0, result.config().entity().maxHealth());
        assertEquals(0.62, result.config().flight().horizontalSpeed());
        assertEquals(60, result.config().naturalSpawning().checkInterval().toSeconds());
        assertTrue(result.hasWarnings());
    }

    @Test
    void emptyTameItemListFallsBackInsteadOfCreatingUntameablePegasus() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("taming.items", List.of());
        PegasusConfig config = ConfigValidator.validate(new MapConfigSource(raw)).config();
        assertEquals(List.of("GOLDEN_APPLE"), config.taming().items());
    }

    @Test
    void emptyAllowedWorldsMeansEveryWorldRatherThanSilentlyOff() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("natural-spawning.allowed-worlds", List.of("only_this_one"));
        PegasusConfig config = ConfigValidator.validate(new MapConfigSource(raw)).config();
        assertTrue(config.naturalSpawning().allowsWorld("only_this_one"));
        assertTrue(config.naturalSpawning().allowsWorld("ONLY_THIS_ONE"));
        assertFalse(config.naturalSpawning().allowsWorld("world_nether"));
    }

    @Test
    void disabledNaturalSpawningNeverAllowsAWorld() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("natural-spawning.enabled", false);
        PegasusConfig config = ConfigValidator.validate(new MapConfigSource(raw)).config();
        assertFalse(config.naturalSpawning().allowsWorld("world"));
    }

    @Test
    void unknownMessageKeysAreReportedButIgnored() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("messages", Map.of("not-a-real-key", "hello"));
        ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(raw));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not-a-real-key")));
    }

    @Test
    void configuredMessageOverridesAreApplied() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("messages", Map.of("tame-success", "Bonded!"));
        PegasusConfig config = ConfigValidator.validate(new MapConfigSource(raw)).config();
        assertEquals("Bonded!", config.messages().get("tame-success"));
    }
}
