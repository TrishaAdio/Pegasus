package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BreedingServiceTest {
    private final BreedingRules.Settings settings = new BreedingRules.Settings(Duration.ofMinutes(5), 100, 50);

    @Test
    void bothPreparedUnicornsGiveGuaranteedPegasus() {
        BreedingService service = new BreedingService(Duration.ofMinutes(5));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        service.prepare(a, 1_000);
        service.prepare(b, 1_000);
        assertEquals(100, service.consumeForBreeding(a, b, 2_000, settings));
    }

    @Test
    void onePreparedUnicornGivesHalfChance() {
        BreedingService service = new BreedingService(Duration.ofMinutes(5));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        service.prepare(a, 1_000);
        assertEquals(50, service.consumeForBreeding(a, b, 2_000, settings));
    }

    @Test
    void unpreparedPairCannotProducePegasus() {
        BreedingService service = new BreedingService(Duration.ofMinutes(5));
        assertEquals(0, service.consumeForBreeding(UUID.randomUUID(), UUID.randomUUID(), 2_000, settings));
    }

    @Test
    void preparationIsConsumedSoOneStarCannotBeReused() {
        BreedingService service = new BreedingService(Duration.ofMinutes(5));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        service.prepare(a, 1_000);
        service.prepare(b, 1_000);
        assertEquals(100, service.consumeForBreeding(a, b, 2_000, settings));
        assertEquals(0, service.consumeForBreeding(a, b, 3_000, settings));
        assertEquals(0, service.trackedCount());
    }

    @Test
    void expiredPreparationIsIgnoredAndPruned() {
        BreedingService service = new BreedingService(Duration.ofSeconds(30));
        UUID a = UUID.randomUUID();
        service.prepare(a, 0);
        assertTrue(service.isPrepared(a, 10_000));
        assertFalse(service.isPrepared(a, 40_000));
        assertEquals(0, service.trackedCount());
    }

    @Test
    void purgeExpiredKeepsStateBounded() {
        BreedingService service = new BreedingService(Duration.ofSeconds(10));
        for (int i = 0; i < 50; i++) {
            service.prepare(UUID.randomUUID(), 0);
        }
        assertEquals(50, service.trackedCount());
        assertEquals(50, service.purgeExpired(60_000));
        assertEquals(0, service.trackedCount());
    }
}
