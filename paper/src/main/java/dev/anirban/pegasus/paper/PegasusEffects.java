/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.animation.AnimationState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Horse;

/**
 * Turns the shared animation state into sound and particle feedback on Paper.
 *
 * <p><b>Scope, stated plainly:</b> Paper's public API cannot register a custom entity type or attach
 * custom geometry to a vanilla horse, so this class does <i>not</i> render wings. What it does is
 * make the shared {@link AnimationState} machine observable in-game: a wing-beat sound and feather
 * particles on takeoff and while flying, and a thud on landing. Fabric uses the same state machine
 * to drive a real animated model.
 *
 * <p>Effects fire only on state transitions and on a throttled interval while flying, so a long
 * flight cannot flood clients with packets.
 */
public final class PegasusEffects {
    /** Minimum gap between repeating in-flight wing beats, in milliseconds. */
    private static final long FLAP_INTERVAL_MILLIS = 620L;

    private final Map<UUID, AnimationState> lastState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlap = new ConcurrentHashMap<>();

    /** Called once per flight tick with the freshly resolved state. */
    public void apply(Horse pegasus, AnimationState state, long nowMillis) {
        UUID id = pegasus.getUniqueId();
        AnimationState previous = lastState.put(id, state);

        if (previous != state) {
            onEnter(pegasus, state);
        }
        if (state == AnimationState.FLY || state == AnimationState.WING_FLAP || state == AnimationState.TAKEOFF) {
            Long previousFlap = lastFlap.get(id);
            if (previousFlap == null || nowMillis - previousFlap >= FLAP_INTERVAL_MILLIS) {
                lastFlap.put(id, nowMillis);
                wingBeat(pegasus);
            }
        }
    }

    private void onEnter(Horse pegasus, AnimationState state) {
        Location at = pegasus.getLocation();
        switch (state) {
            case TAKEOFF -> {
                pegasus.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55f, 1.5f);
                pegasus.getWorld().spawnParticle(Particle.CLOUD, at, 8, 0.6, 0.2, 0.6, 0.02);
            }
            case LANDING -> {
                pegasus.getWorld().playSound(at, Sound.ENTITY_HORSE_LAND, 0.7f, 1.0f);
                pegasus.getWorld().spawnParticle(Particle.CLOUD, at, 10, 0.7, 0.1, 0.7, 0.01);
            }
            case HURT -> pegasus.getWorld().playSound(at, Sound.ENTITY_HORSE_HURT, 0.7f, 1.1f);
            case DEATH -> pegasus.getWorld().playSound(at, Sound.ENTITY_HORSE_DEATH, 0.8f, 1.0f);
            default -> {
                // IDLE, WALK, RUN, FLY, WING_FLAP and EAT need no one-shot effect.
            }
        }
    }

    private void wingBeat(Horse pegasus) {
        Location at = pegasus.getLocation().add(0, 1.0, 0);
        pegasus.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.28f, 1.8f);
        // Feather-like drift to either side of the body, standing in for a wing sweep.
        pegasus.getWorld().spawnParticle(Particle.CLOUD, at, 4, 1.1, 0.15, 0.5, 0.005);
    }

    /** Clears per-entity state on dismount, death or removal. */
    public void forget(UUID pegasusId) {
        lastState.remove(pegasusId);
        lastFlap.remove(pegasusId);
    }

    public int trackedCount() {
        return lastState.size();
    }
}
