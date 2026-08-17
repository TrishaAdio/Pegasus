/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic per-UUID cooldown gate used for tame attempts and takeoff spam.
 *
 * <p>Entries are pruned as they expire so a long-running server does not accumulate one entry per
 * player/entity that ever interacted.
 */
public final class CooldownTracker {
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final Duration cooldown;

    public CooldownTracker(Duration cooldown) {
        if (cooldown == null || cooldown.isNegative()) {
            throw new IllegalArgumentException("Cooldown cannot be negative");
        }
        this.cooldown = cooldown;
    }

    /** Returns true and starts the cooldown when the action is allowed. */
    public boolean tryUse(UUID id, long nowMillis) {
        if (id == null) {
            return false;
        }
        if (cooldown.isZero()) {
            return true;
        }
        Long previous = lastUse.get(id);
        if (previous != null && nowMillis - previous < cooldown.toMillis() && nowMillis >= previous) {
            return false;
        }
        lastUse.put(id, nowMillis);
        return true;
    }

    public void clear(UUID id) {
        if (id != null) {
            lastUse.remove(id);
        }
    }

    public int purgeExpired(long nowMillis) {
        int before = lastUse.size();
        lastUse.entrySet().removeIf(entry -> nowMillis - entry.getValue() >= cooldown.toMillis());
        return before - lastUse.size();
    }

    public int trackedCount() {
        return lastUse.size();
    }
}
