/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.SpawnPlatformRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;

/**
 * Finds Hay Bale platforms high in the sky and occasionally spawns a Pegasus on one.
 *
 * <p>Cost control matters here: this runs on a repeating main-thread task, so the scan is bounded to
 * a fixed square around each player, only reads already-loaded chunks, and stops at the first valid
 * platform per player per pass. It never loads or generates chunks, which is what makes an
 * "innocent" world scan stall a server.
 */
public final class NaturalSpawnService {
    /** Half-width of the search box around a player, in blocks. */
    private static final int SEARCH_RADIUS = 24;
    /** Vertical band searched around the player for a platform surface. */
    private static final int VERTICAL_RADIUS = 12;

    private final PegasusEntities entities;
    private final Random random;
    private volatile PegasusConfig config;

    public NaturalSpawnService(PegasusEntities entities, PegasusConfig config, Random random) {
        this.entities = entities;
        this.config = config;
        this.random = random;
    }

    public void updateConfig(PegasusConfig config) {
        this.config = config;
    }

    /** Result of one scan pass, used for concise debug logging. */
    public record Outcome(boolean spawned, String reason, Location location) {
        static Outcome skip(String reason) {
            return new Outcome(false, reason, null);
        }
    }

    /**
     * Runs one spawn attempt for a single player.
     *
     * @param debug sink for verbose diagnostics; only called when debug mode is enabled
     */
    public Outcome attemptForPlayer(Player player, Consumer<String> debug) {
        PegasusConfig current = config;
        PegasusConfig.NaturalSpawning settings = current.naturalSpawning();

        World world = player.getWorld();
        if (!settings.allowsWorld(world.getName())) {
            return Outcome.skip("world-not-allowed");
        }

        Material required = resolveBlock(settings.requiredBlock()).orElse(null);
        if (required == null) {
            return Outcome.skip("invalid-required-block");
        }

        Optional<Block> candidate = findPlatformSurface(player, required, settings);
        if (candidate.isEmpty()) {
            return Outcome.skip("no-platform-found");
        }
        Block surface = candidate.get();

        List<Boolean> scan = scanPlatform(surface, required, settings.platform());
        SpawnPlatformRules.Result validation =
                SpawnPlatformRules.validate(surface.getY(), scan, settings.platform());
        if (!validation.valid()) {
            debug.accept("platform rejected at " + describe(surface) + ": " + validation.reason()
                    + " (" + validation.matchingBlocks() + "/" + validation.scannedBlocks() + " blocks)");
            return Outcome.skip(validation.reason());
        }

        debug.accept("valid platform at " + describe(surface) + " ("
                + validation.matchingBlocks() + "/" + validation.scannedBlocks() + " blocks)");

        Location spawnAt = surface.getLocation().add(0.5, 1.0, 0.5);
        if (countNearbyPegasus(spawnAt, settings.nearbyRadius()) > settings.maxNearbyPegasus()) {
            return Outcome.skip("too-many-nearby");
        }
        // Roll last so a failed roll costs nothing but the (cheap) validation above.
        if (random.nextInt(100) >= settings.spawnChancePercent()) {
            return Outcome.skip("chance-not-met");
        }
        if (!spawnAt.getBlock().isEmpty() || !spawnAt.clone().add(0, 1, 0).getBlock().isEmpty()) {
            return Outcome.skip("no-headroom");
        }

        Horse horse = world.spawn(spawnAt, Horse.class, spawned ->
                entities.initialisePegasus(spawned, PegasusVariant.CLASSIC, current, false));
        return new Outcome(true, "spawned", horse.getLocation());
    }

    /** Locates the topmost required block near the player that could be a platform surface. */
    private Optional<Block> findPlatformSurface(Player player, Material required,
                                                PegasusConfig.NaturalSpawning settings) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        int baseY = origin.getBlockY();
        int minY = Math.max(settings.platform().minimumY() + 1, baseY - VERTICAL_RADIUS);
        int maxY = Math.min(world.getMaxHeight() - 2, baseY + VERTICAL_RADIUS);
        if (minY > maxY) {
            return Optional.empty();
        }

        for (int y = maxY; y >= minY; y--) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += 4) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += 4) {
                    int x = origin.getBlockX() + dx;
                    int z = origin.getBlockZ() + dz;
                    // Only inspect loaded chunks; never trigger generation from a scheduled task.
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == required) {
                        return Optional.of(block);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Samples the platform layer around a found block.
     *
     * <p>Returns one boolean per inspected column: true when it is the required block. Sampling a
     * bounded square keeps the cost predictable regardless of how large the player's build is.
     */
    private List<Boolean> scanPlatform(Block surface, Material required, SpawnPlatformRules.Settings platform) {
        // Side length chosen so area comfortably exceeds the configured minimum block count.
        int side = (int) Math.ceil(Math.sqrt(platform.minimumPlatformBlocks())) + 2;
        int half = Math.max(1, side / 2);
        World world = surface.getWorld();
        List<Boolean> results = new ArrayList<>();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = surface.getX() + dx;
                int z = surface.getZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                results.add(world.getBlockAt(x, surface.getY(), z).getType() == required);
            }
        }
        return results;
    }

    private long countNearbyPegasus(Location location, int radius) {
        return location.getWorld().getNearbyEntities(location, radius, radius, radius).stream()
                .filter(entities::isPegasus)
                .count();
    }

    /** Resolves a configured block name without throwing on a typo. */
    public static Optional<Material> resolveBlock(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(name.strip().toUpperCase(Locale.ROOT));
        return material != null && material.isBlock() ? Optional.of(material) : Optional.empty();
    }

    private static String describe(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
