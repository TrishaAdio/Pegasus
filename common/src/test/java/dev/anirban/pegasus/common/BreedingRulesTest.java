package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BreedingRulesTest {
    private final long now = 10_000L;
    private final BreedingRules.Settings settings = new BreedingRules.Settings(Duration.ofMinutes(5), 100, 50);

    @Test void twoPreparedUnicornsHaveGuaranteedPegasusChance() {
        var a = new BreedingRules.Preparation(UUID.randomUUID(), now - 1);
        var b = new BreedingRules.Preparation(UUID.randomUUID(), now - 1);
        assertEquals(100, BreedingRules.successChance(a, b, now, settings));
        assertTrue(BreedingRules.succeeds(100, 100));
    }

    @Test void onePreparedUnicornHasConfiguredHalfChance() {
        var a = new BreedingRules.Preparation(UUID.randomUUID(), now - 1);
        assertEquals(50, BreedingRules.successChance(a, null, now, settings));
        assertTrue(BreedingRules.succeeds(50, 50));
        assertFalse(BreedingRules.succeeds(50, 51));
    }

    @Test void missingOrExpiredPreparationCannotProducePegasus() {
        var expired = new BreedingRules.Preparation(UUID.randomUUID(), now - Duration.ofMinutes(6).toMillis());
        assertEquals(0, BreedingRules.successChance(expired, null, now, settings));
        assertFalse(BreedingRules.succeeds(0, 1));
    }
}
