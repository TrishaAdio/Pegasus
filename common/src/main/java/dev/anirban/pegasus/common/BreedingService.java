/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the temporary "prepared with a Nether Star" state per Unicorn.
 *
 * <p>State is deliberately transient (not persisted): a preparation window is short-lived, and
 * persisting it would let a stale star survive a restart. Expired entries are pruned on read and
 * via {@link #purgeExpired(long)} so this map cannot grow without bound — the old-style
 * "remember every entity forever" approach is a memory leak.
 */
public final class BreedingService {
    private final ConcurrentHashMap<UUID, BreedingRules.Preparation> prepared = new ConcurrentHashMap<>();
    private final Duration window;

    public BreedingService(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Preparation window must be positive");
        }
        this.window = window;
    }

    /** Marks a Unicorn as prepared. Re-preparing simply refreshes the timestamp. */
    public void prepare(UUID unicornId, long nowMillis) {
        if (unicornId == null) {
            throw new IllegalArgumentException("Unicorn UUID cannot be null");
        }
        prepared.put(unicornId, new BreedingRules.Preparation(unicornId, nowMillis));
    }

    public boolean isPrepared(UUID unicornId, long nowMillis) {
        return preparation(unicornId, nowMillis).isPresent();
    }

    /** Returns the live preparation, removing it if the window has elapsed. */
    public Optional<BreedingRules.Preparation> preparation(UUID unicornId, long nowMillis) {
        if (unicornId == null) {
            return Optional.empty();
        }
        BreedingRules.Preparation existing = prepared.get(unicornId);
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.activeAt(nowMillis, window)) {
            prepared.remove(unicornId, existing);
            return Optional.empty();
        }
        return Optional.of(existing);
    }

    /**
     * Resolves the Pegasus chance for a breeding attempt and consumes both preparations, so a
     * single Nether Star cannot be reused across multiple breedings.
     */
    public int consumeForBreeding(UUID firstUnicorn, UUID secondUnicorn, long nowMillis,
                                  BreedingRules.Settings settings) {
        BreedingRules.Preparation first = preparation(firstUnicorn, nowMillis).orElse(null);
        BreedingRules.Preparation second = preparation(secondUnicorn, nowMillis).orElse(null);
        int chance = BreedingRules.successChance(first, second, nowMillis, settings);
        if (firstUnicorn != null) {
            prepared.remove(firstUnicorn);
        }
        if (secondUnicorn != null) {
            prepared.remove(secondUnicorn);
        }
        return chance;
    }

    public void forget(UUID unicornId) {
        if (unicornId != null) {
            prepared.remove(unicornId);
        }
    }

    /** Periodic cleanup so removed/dead Unicorns cannot retain state. */
    public int purgeExpired(long nowMillis) {
        int before = prepared.size();
        prepared.entrySet().removeIf(entry -> !entry.getValue().activeAt(nowMillis, window));
        return before - prepared.size();
    }

    public int trackedCount() {
        return prepared.size();
    }

    public Map<UUID, BreedingRules.Preparation> snapshot() {
        return Map.copyOf(prepared);
    }
}
