/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.common.config;

import dev.anirban.pegasus.common.BreedingRules;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.SpawnPlatformRules;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns raw configuration into a valid {@link PegasusConfig}.
 *
 * <p>Design rule: a malformed config must never prevent the server from starting. Out-of-range and
 * unparseable values are clamped or replaced with the documented default and reported as warnings,
 * which the platform prints in yellow. Only truly structural problems would surface as failures.
 */
public final class ConfigValidator {
    private ConfigValidator() { }

    public record Result(PegasusConfig config, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    public static Result validate(ConfigSource source) {
        List<String> warnings = new ArrayList<>();
        PegasusConfig defaults = PegasusConfig.defaults();

        PegasusConfig.Entity entity = new PegasusConfig.Entity(
                clampDouble(source, "entity.max-health", defaults.entity().maxHealth(), 1, 1024, warnings),
                clampDouble(source, "entity.movement-speed", defaults.entity().movementSpeed(), 0.01, 2, warnings),
                clampDouble(source, "entity.baby-scale", defaults.entity().babyScale(), 0.1, 1, warnings));

        List<String> tameItems = stringList(source, "taming.items", defaults.taming().items(), warnings);
        PegasusConfig.Taming taming = new PegasusConfig.Taming(
                tameItems,
                clampInt(source, "taming.chance-percent", defaults.taming().chancePercent(), 0, 100, warnings),
                seconds(source, "taming.cooldown-seconds", defaults.taming().cooldown(), 0, 3600, warnings),
                bool(source, "taming.require-saddle-to-ride", defaults.taming().requireSaddleToRide()));

        PegasusConfig.Flight flight = new PegasusConfig.Flight(
                clampDouble(source, "flight.horizontal-speed", defaults.flight().horizontalSpeed(), 0.05, 3, warnings),
                clampDouble(source, "flight.vertical-speed", defaults.flight().verticalSpeed(), 0.05, 3, warnings),
                clampDouble(source, "flight.sprint-multiplier", defaults.flight().sprintMultiplier(), 1, 4, warnings),
                bool(source, "flight.prevent-fall-damage", defaults.flight().preventFallDamage()),
                millis(source, "flight.takeoff-cooldown-millis", defaults.flight().takeoffCooldown(), 0, 60_000, warnings));

        SpawnPlatformRules.Settings platform = new SpawnPlatformRules.Settings(
                clampInt(source, "natural-spawning.minimum-y-level",
                        defaults.naturalSpawning().platform().minimumY(), -64, 319, warnings),
                clampInt(source, "natural-spawning.minimum-platform-blocks",
                        defaults.naturalSpawning().platform().minimumPlatformBlocks(), 1, 4096, warnings),
                clampDouble(source, "natural-spawning.required-block-ratio",
                        defaults.naturalSpawning().platform().requiredBlockRatio(), 0.1, 1.0, warnings));

        PegasusConfig.NaturalSpawning naturalSpawning = new PegasusConfig.NaturalSpawning(
                bool(source, "natural-spawning.enabled", defaults.naturalSpawning().enabled()),
                identifier(source, "natural-spawning.required-block", defaults.naturalSpawning().requiredBlock(), warnings),
                platform,
                seconds(source, "natural-spawning.check-interval-seconds",
                        defaults.naturalSpawning().checkInterval(), 5, 3600, warnings),
                clampInt(source, "natural-spawning.spawn-chance-percent",
                        defaults.naturalSpawning().spawnChancePercent(), 0, 100, warnings),
                clampInt(source, "natural-spawning.max-nearby-pegasus",
                        defaults.naturalSpawning().maxNearbyPegasus(), 0, 64, warnings),
                clampInt(source, "natural-spawning.nearby-radius",
                        defaults.naturalSpawning().nearbyRadius(), 4, 256, warnings),
                stringList(source, "natural-spawning.allowed-worlds",
                        defaults.naturalSpawning().allowedWorlds(), warnings));

        BreedingRules.Settings breedingRules = new BreedingRules.Settings(
                seconds(source, "breeding.preparation-window-seconds",
                        defaults.breeding().preparationWindow(), 1, 86_400, warnings),
                clampInt(source, "breeding.both-prepared-chance-percent",
                        defaults.breeding().bothPreparedChance(), 0, 100, warnings),
                clampInt(source, "breeding.one-prepared-chance-percent",
                        defaults.breeding().onePreparedChance(), 0, 100, warnings));

        PegasusConfig.Breeding breedingItems = new PegasusConfig.Breeding(
                identifier(source, "breeding.preparation-item", defaults.breedingItems().preparationItem(), warnings),
                identifier(source, "breeding.trigger-item", defaults.breedingItems().triggerItem(), warnings),
                seconds(source, "breeding.cooldown-seconds", defaults.breedingItems().cooldown(), 0, 86_400, warnings));

        PegasusConfig.Eggs eggs = new PegasusConfig.Eggs(
                bool(source, "spawn-eggs.require-permission", defaults.eggs().requirePermission()),
                bool(source, "spawn-eggs.creative-tab", defaults.eggs().creativeTab()),
                source.getString("spawn-eggs.display-name").orElse(defaults.eggs().displayNameFormat()),
                stringList(source, "spawn-eggs.lore", defaults.eggs().lore(), warnings));

        Map<String, String> messageOverrides = source.getStringMap("messages");
        messageOverrides.keySet().stream()
                .filter(key -> !Messages.defaults().containsKey(key))
                .forEach(key -> warnings.add("messages." + key + " is not a known message key and will be ignored"));

        PegasusConfig config = new PegasusConfig(entity, taming, flight, naturalSpawning, breedingRules,
                breedingItems, eggs, bool(source, "debug", defaults.debug()), new Messages(messageOverrides));

        return new Result(config, warnings);
    }

    private static double clampDouble(ConfigSource source, String path, double fallback,
                                      double min, double max, List<String> warnings) {
        Number raw = source.getNumber(path).orElse(null);
        if (raw == null) {
            if (source.getString(path).isPresent()) {
                warnings.add(path + " is not a number; using default " + fallback);
            }
            return fallback;
        }
        double value = raw.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            warnings.add(path + " is not a finite number; using default " + fallback);
            return fallback;
        }
        if (value < min || value > max) {
            double clamped = Math.min(max, Math.max(min, value));
            warnings.add(path + "=" + value + " is outside " + min + ".." + max + "; clamped to " + clamped);
            return clamped;
        }
        return value;
    }

    private static int clampInt(ConfigSource source, String path, int fallback,
                                int min, int max, List<String> warnings) {
        Number raw = source.getNumber(path).orElse(null);
        if (raw == null) {
            if (source.getString(path).isPresent()) {
                warnings.add(path + " is not a number; using default " + fallback);
            }
            return fallback;
        }
        double value = raw.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            warnings.add(path + " is not a finite number; using default " + fallback);
            return fallback;
        }
        long rounded = Math.round(value);
        if (rounded < min || rounded > max) {
            long clamped = Math.min(max, Math.max(min, rounded));
            warnings.add(path + "=" + rounded + " is outside " + min + ".." + max + "; clamped to " + clamped);
            return (int) clamped;
        }
        return (int) rounded;
    }

    private static boolean bool(ConfigSource source, String path, boolean fallback) {
        return source.getBoolean(path).orElse(fallback);
    }

    private static Duration seconds(ConfigSource source, String path, Duration fallback,
                                    int min, int max, List<String> warnings) {
        int value = clampInt(source, path, (int) fallback.toSeconds(), min, max, warnings);
        return Duration.ofSeconds(value);
    }

    private static Duration millis(ConfigSource source, String path, Duration fallback,
                                   int min, int max, List<String> warnings) {
        int value = clampInt(source, path, (int) fallback.toMillis(), min, max, warnings);
        return Duration.ofMillis(value);
    }

    private static List<String> stringList(ConfigSource source, String path,
                                           List<String> fallback, List<String> warnings) {
        List<String> values = source.getStringList(path);
        if (values.isEmpty()) {
            if (source.getString(path).isPresent()) {
                warnings.add(path + " should be a list; using default " + fallback);
            }
            return fallback;
        }
        return values;
    }

    /**
     * Normalises a block/item name without asserting that the platform actually has it — the
     * registry lookup happens in the platform adapter, which reports an unknown id as a warning.
     */
    private static String identifier(ConfigSource source, String path, String fallback, List<String> warnings) {
        String value = source.getString(path).orElse(null);
        if (value == null) {
            return fallback;
        }
        String normalised = value.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalised.isEmpty()) {
            warnings.add(path + " is empty; using default " + fallback);
            return fallback;
        }
        return normalised;
    }
}
