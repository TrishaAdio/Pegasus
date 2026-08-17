/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Fully validated, immutable settings shared by both platforms.
 *
 * <p>Every nested record validates its own invariants in its canonical constructor, so an invalid
 * {@code PegasusConfig} cannot exist. Turning bad user input into good values is the job of
 * {@link dev.anirban.pegasus.common.config.ConfigValidator}, which clamps and reports instead of
 * throwing.
 */
public record PegasusConfig(
        Entity entity,
        Taming taming,
        Flight flight,
        NaturalSpawning naturalSpawning,
        BreedingRules.Settings breeding,
        Breeding breedingItems,
        Eggs eggs,
        boolean debug,
        Messages messages) {

    public PegasusConfig {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(taming, "taming");
        Objects.requireNonNull(flight, "flight");
        Objects.requireNonNull(naturalSpawning, "naturalSpawning");
        Objects.requireNonNull(breeding, "breeding");
        Objects.requireNonNull(breedingItems, "breedingItems");
        Objects.requireNonNull(eggs, "eggs");
        Objects.requireNonNull(messages, "messages");
    }

    /** Base attributes applied on spawn. */
    public record Entity(double maxHealth, double movementSpeed, double babyScale) {
        public Entity {
            requirePositive(maxHealth, "max-health");
            requirePositive(movementSpeed, "movement-speed");
            if (babyScale <= 0 || babyScale > 1) {
                throw new IllegalArgumentException("baby-scale must be > 0 and <= 1");
            }
        }
    }

    /** Taming item is an original Java Edition design choice, documented in the README. */
    public record Taming(List<String> items, int chancePercent, Duration cooldown,
                         boolean requireSaddleToRide) {
        public Taming {
            items = List.copyOf(Objects.requireNonNull(items, "tame items"));
            if (items.isEmpty()) {
                throw new IllegalArgumentException("At least one tame item is required");
            }
            requirePercent(chancePercent, "tame-chance-percent");
            requireNonNegativeDuration(cooldown, "tame-cooldown");
        }
    }

    public record Flight(double horizontalSpeed, double verticalSpeed, double sprintMultiplier,
                         boolean preventFallDamage, Duration takeoffCooldown) {
        public Flight {
            requirePositive(horizontalSpeed, "flight horizontal-speed");
            requirePositive(verticalSpeed, "flight vertical-speed");
            if (sprintMultiplier < 1) {
                throw new IllegalArgumentException("sprint-multiplier must be >= 1");
            }
            requireNonNegativeDuration(takeoffCooldown, "takeoff-cooldown");
        }
    }

    public record NaturalSpawning(boolean enabled, String requiredBlock, SpawnPlatformRules.Settings platform,
                                  Duration checkInterval, int spawnChancePercent, int maxNearbyPegasus,
                                  int nearbyRadius, List<String> allowedWorlds) {
        public NaturalSpawning {
            Objects.requireNonNull(platform, "platform");
            if (requiredBlock == null || requiredBlock.isBlank()) {
                throw new IllegalArgumentException("required-block must be set");
            }
            requiredBlock = requiredBlock.strip();
            if (checkInterval == null || checkInterval.isNegative() || checkInterval.isZero()) {
                throw new IllegalArgumentException("check-interval must be positive");
            }
            requirePercent(spawnChancePercent, "spawn-chance-percent");
            if (maxNearbyPegasus < 0) {
                throw new IllegalArgumentException("max-nearby-pegasus cannot be negative");
            }
            if (nearbyRadius < 1) {
                throw new IllegalArgumentException("nearby-radius must be positive");
            }
            allowedWorlds = List.copyOf(Objects.requireNonNull(allowedWorlds, "allowed-worlds"));
        }

        public boolean allowsWorld(String worldName) {
            if (!enabled) {
                return false;
            }
            // An empty list is treated as "every world", which is friendlier than silently disabling.
            return allowedWorlds.isEmpty() || allowedWorlds.stream().anyMatch(name -> name.equalsIgnoreCase(worldName));
        }
    }

    /** Item names driving the Unicorn breeding flow. */
    public record Breeding(String preparationItem, String triggerItem, Duration cooldown) {
        public Breeding {
            if (preparationItem == null || preparationItem.isBlank()) {
                throw new IllegalArgumentException("breeding preparation-item must be set");
            }
            if (triggerItem == null || triggerItem.isBlank()) {
                throw new IllegalArgumentException("breeding trigger-item must be set");
            }
            preparationItem = preparationItem.strip();
            triggerItem = triggerItem.strip();
            requireNonNegativeDuration(cooldown, "breeding cooldown");
        }
    }

    public record Eggs(boolean requirePermission, boolean creativeTab, String displayNameFormat, List<String> lore) {
        public Eggs {
            if (displayNameFormat == null || displayNameFormat.isBlank()) {
                throw new IllegalArgumentException("egg display-name must be set");
            }
            displayNameFormat = displayNameFormat.strip();
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    /** The documented shipped defaults. Kept in one place so docs and code cannot drift. */
    public static PegasusConfig defaults() {
        return new PegasusConfig(
                new Entity(30.0, 0.2825, 0.5),
                new Taming(List.of("GOLDEN_APPLE"), 35, Duration.ofSeconds(3), true),
                new Flight(0.62, 0.42, 1.5, true, Duration.ofMillis(750)),
                new NaturalSpawning(true, "HAY_BLOCK",
                        new SpawnPlatformRules.Settings(175, 128, 0.9),
                        Duration.ofSeconds(60), 10, 1, 48, List.of("world")),
                new BreedingRules.Settings(Duration.ofMinutes(5), 100, 50),
                new Breeding("NETHER_STAR", "GOLDEN_CARROT", Duration.ofMinutes(5)),
                new Eggs(false, true, "{variant} Spawn Egg",
                        List.of("Pegasus \u2014 Created by Anirban <3")),
                false,
                new Messages(null));
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0) || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a positive finite number");
        }
    }

    private static void requirePercent(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }

    private static void requireNonNegativeDuration(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }
}
