/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.CooldownTracker;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.animation.AnimationResolver;
import dev.anirban.pegasus.common.animation.AnimationState;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Server-authoritative Pegasus flight.
 *
 * <p><b>Control scheme (an original Java Edition design decision, not observed behaviour):</b>
 * jump to take off, look where you want to go, sneak to descend and land. Paper 1.21.1 does not
 * expose per-tick client key state ({@code Player#getCurrentInput()} arrived in a later version),
 * so steering is derived from the rider's look direction, which needs no client mod and stays
 * perfectly in sync.
 *
 * <p>Movement is applied with {@link Entity#setVelocity(Vector)} on the main thread. Paper
 * replicates vehicle velocity to all tracking clients, so multiplayer observers see the same motion
 * as the rider.
 */
public final class FlightController {
    /** Vertical velocity floor applied while gliding so a Pegasus hovers instead of stone-dropping. */
    private static final double HOVER_LIFT = -0.055;
    private static final double PITCH_CLIMB_SCALE = 0.9;

    private final Map<UUID, AnimationResolver> resolvers = new ConcurrentHashMap<>();
    private final Set<UUID> airborne = ConcurrentHashMap.newKeySet();
    private final Set<UUID> descending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> noFallDamage = ConcurrentHashMap.newKeySet();
    private final CooldownTracker takeoffCooldown;
    private final PegasusConfig config;

    public FlightController(PegasusConfig config) {
        this.config = config;
        this.takeoffCooldown = new CooldownTracker(config.flight().takeoffCooldown());
    }

    /** Called from {@code HorseJumpEvent}; returns true when the jump became a takeoff. */
    public boolean requestTakeoff(Horse pegasus, long nowMillis) {
        if (!takeoffCooldown.tryUse(pegasus.getUniqueId(), nowMillis)) {
            return false;
        }
        airborne.add(pegasus.getUniqueId());
        Vector velocity = pegasus.getVelocity().clone();
        velocity.setY(Math.max(velocity.getY(), config.flight().verticalSpeed() * 1.4));
        pegasus.setVelocity(velocity);
        return true;
    }

    public void setDescending(UUID pegasusId, boolean value) {
        if (value) {
            descending.add(pegasusId);
        } else {
            descending.remove(pegasusId);
        }
    }

    public boolean isAirborne(UUID pegasusId) {
        return airborne.contains(pegasusId);
    }

    /**
     * Advances one tick of flight for a ridden Pegasus and returns the animation state to render.
     *
     * @param rider the controlling player, already validated as the owner
     */
    public AnimationState tick(Horse pegasus, Player rider, long nowMillis) {
        UUID id = pegasus.getUniqueId();
        boolean onGround = pegasus.isOnGround();
        Vector velocity = pegasus.getVelocity();

        if (onGround && !descending.contains(id) && velocity.getY() <= 0) {
            // Touchdown: leave flight mode so vanilla ground movement resumes cleanly.
            airborne.remove(id);
            noFallDamage.remove(id);
        }

        if (airborne.contains(id) && !pegasus.isDead()) {
            applyFlight(pegasus, rider, id);
        }

        AnimationResolver resolver = resolvers.computeIfAbsent(id, ignored -> new AnimationResolver());
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        return resolver.resolve(new AnimationResolver.Input(
                pegasus.isDead(),
                pegasus.getNoDamageTicks() > 0,
                false,
                onGround,
                airborne.contains(id),
                horizontal,
                velocity.getY(),
                velocity.getY() > 0.08), nowMillis);
    }

    private void applyFlight(Horse pegasus, Player rider, UUID id) {
        Location eye = rider.getLocation();
        Vector direction = eye.getDirection().normalize();

        double speed = config.flight().horizontalSpeed();
        if (rider.isSprinting()) {
            speed *= config.flight().sprintMultiplier();
        }

        Vector horizontal = new Vector(direction.getX(), 0, direction.getZ());
        if (horizontal.lengthSquared() > 1.0E-6) {
            horizontal.normalize().multiply(speed);
        }

        // Pitch drives climb/dive: looking up climbs, looking down dives.
        double vertical = direction.getY() * config.flight().verticalSpeed() * PITCH_CLIMB_SCALE;
        if (descending.contains(id)) {
            vertical = -config.flight().verticalSpeed();
        } else if (vertical < HOVER_LIFT) {
            vertical = HOVER_LIFT;
        }

        Vector next = new Vector(horizontal.getX(), vertical, horizontal.getZ());
        if (!isFinite(next)) {
            // Never hand a NaN velocity to the server; that corrupts entity position permanently.
            return;
        }

        pegasus.setVelocity(next);
        pegasus.setFallDistance(0.0f);
        if (config.flight().preventFallDamage()) {
            noFallDamage.add(id);
        }
        preventSuffocation(pegasus);
    }

    /**
     * Nudges the Pegasus out of a solid block if flight pushed it into terrain, which is what causes
     * riders to get stuck inside walls.
     */
    private void preventSuffocation(Horse pegasus) {
        Location location = pegasus.getLocation();
        if (location.getBlock().getType().isSolid()) {
            Location safe = location.clone().add(0, 1, 0);
            if (!safe.getBlock().getType().isSolid()) {
                pegasus.teleport(safe);
            }
        }
    }

    /** True when fall damage should be cancelled for this entity. */
    public boolean shouldCancelFallDamage(UUID pegasusId) {
        return config.flight().preventFallDamage()
                && (noFallDamage.contains(pegasusId) || airborne.contains(pegasusId));
    }

    /** Clears all per-entity state; called on dismount, death and removal to avoid leaks. */
    public void forget(UUID pegasusId) {
        airborne.remove(pegasusId);
        descending.remove(pegasusId);
        noFallDamage.remove(pegasusId);
        resolvers.remove(pegasusId);
        takeoffCooldown.clear(pegasusId);
    }

    /** Periodic housekeeping so long uptime cannot accumulate stale entries. */
    public void purge(Set<UUID> liveEntityIds, long nowMillis) {
        Set<UUID> tracked = new HashSet<>(resolvers.keySet());
        tracked.addAll(airborne);
        for (UUID id : tracked) {
            if (!liveEntityIds.contains(id)) {
                forget(id);
            }
        }
        takeoffCooldown.purgeExpired(nowMillis);
    }

    public int trackedCount() {
        return resolvers.size();
    }

    private static boolean isFinite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
    }
}
