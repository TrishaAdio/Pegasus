package dev.anirban.pegasus.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {
    @Test
    void blocksRepeatUseInsideWindowThenAllowsAfterwards() {
        CooldownTracker tracker = new CooldownTracker(Duration.ofSeconds(3));
        UUID player = UUID.randomUUID();
        assertTrue(tracker.tryUse(player, 0));
        assertFalse(tracker.tryUse(player, 2_999));
        assertTrue(tracker.tryUse(player, 3_000));
    }

    @Test
    void zeroCooldownAlwaysAllows() {
        CooldownTracker tracker = new CooldownTracker(Duration.ZERO);
        UUID player = UUID.randomUUID();
        assertTrue(tracker.tryUse(player, 0));
        assertTrue(tracker.tryUse(player, 0));
    }

    @Test
    void expiredEntriesArePrunedToAvoidUnboundedGrowth() {
        CooldownTracker tracker = new CooldownTracker(Duration.ofSeconds(1));
        for (int i = 0; i < 20; i++) {
            tracker.tryUse(UUID.randomUUID(), 0);
        }
        assertEquals(20, tracker.trackedCount());
        tracker.purgeExpired(10_000);
        assertEquals(0, tracker.trackedCount());
    }
}
