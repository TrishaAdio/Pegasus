/*
 * Pegasus Java Edition — Created by Anirban <3
 *
 * Clean-room Java Edition implementation. No third-party add-on code or assets are used.
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.Ansi;
import dev.anirban.pegasus.common.BreedingService;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.StartupReport;
import dev.anirban.pegasus.common.config.ConfigValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Paper entry point: wires configuration, storage, entity systems, tasks and commands. */
public final class PegasusPlugin extends JavaPlugin {
    private static final long TICKS_PER_SECOND = 20L;
    /** How long a pending egg spawn location stays valid, in milliseconds. */
    private static final long PENDING_EGG_TTL = 2_000L;

    private final Map<String, PendingEgg> pendingEggs = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private volatile PegasusConfig config = PegasusConfig.defaults();
    private Ansi ansi = Ansi.autoDetect();
    private PegasusKeys keys;
    private PegasusEntities entities;
    private PaperOwnershipStore ownership;
    private SpawnEggService eggs;
    private BreedingService breeding;
    private FlightController flight;
    private final PegasusEffects effects = new PegasusEffects();
    private NaturalSpawnService naturalSpawn;
    private BukkitTask flightTask;
    private BukkitTask spawnTask;
    private BukkitTask housekeepingTask;

    private record PendingEgg(PegasusVariant variant, long createdAt) { }

    @Override
    public void onEnable() {
        StartupReport report = new StartupReport();

        List<String> warnings = loadConfiguration();
        report.ok("Configuration loaded and validated");
        warnings.forEach(report::warn);

        this.keys = new PegasusKeys(this);
        this.entities = new PegasusEntities(keys);
        this.ownership = new PaperOwnershipStore(keys);
        report.ok("Ownership storage ready (entity persistent data)");

        this.breeding = new BreedingService(config.breeding().preparationWindow());
        this.flight = new FlightController(config);
        this.eggs = new SpawnEggService(keys, config);
        this.naturalSpawn = new NaturalSpawnService(entities, config, random);

        getServer().getPluginManager().registerEvents(
                new PegasusListener(this, entities, ownership, eggs, breeding, random), this);
        report.ok("Pegasus entity system registered");
        report.ok("Unicorn breeding system registered");

        verifyConfiguredMaterials(report);
        report.ok("Spawn eggs registered (" + PegasusVariant.values().length + " variants)");

        registerCommand(report);
        startTasks(report);
        reportResourcePack(report);

        report.emit(ansi, line -> getLogger().info(line));
    }

    @Override
    public void onDisable() {
        cancel(flightTask);
        cancel(spawnTask);
        cancel(housekeepingTask);
        pendingEggs.clear();
        getLogger().info(ansi.info("Pegasus disabled cleanly."));
    }

    // ------------------------------------------------------------------ configuration

    private List<String> loadConfiguration() {
        saveDefaultConfig();
        reloadConfig();
        ConfigValidator.Result result = ConfigValidator.validate(new YamlConfigSource(getConfig()));
        this.config = result.config();
        this.ansi = Ansi.autoDetect();
        return result.warnings();
    }

    /**
     * Reloads configuration and pushes the new values into every live service.
     *
     * @return validation warnings to surface to the caller
     */
    public List<String> reloadPegasusConfig() {
        List<String> warnings = loadConfiguration();
        eggs.updateConfig(config);
        naturalSpawn.updateConfig(config);
        // Flight holds a cooldown derived from config, so replace it rather than mutate it.
        this.flight = new FlightController(config);
        restartTasks();
        return warnings;
    }

    /** Reports unknown material names as warnings instead of failing to enable. */
    private void verifyConfiguredMaterials(StartupReport report) {
        if (NaturalSpawnService.resolveBlock(config.naturalSpawning().requiredBlock()).isEmpty()) {
            report.warn("natural-spawning.required-block '" + config.naturalSpawning().requiredBlock()
                    + "' is not a valid block; natural spawning will not run");
        }
        config.taming().items().stream()
                .filter(name -> org.bukkit.Material.matchMaterial(name) == null)
                .forEach(name -> report.warn("taming.items entry '" + name + "' is not a valid item"));
    }

    private void registerCommand(StartupReport report) {
        PluginCommand command = getCommand("pegasus");
        if (command == null) {
            report.fail("Command /pegasus is missing from plugin.yml");
            return;
        }
        PegasusCommand handler = new PegasusCommand(this, entities, ownership, eggs);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        report.ok("Commands registered (/pegasus)");
    }

    // ------------------------------------------------------------------ tasks

    private void startTasks(StartupReport report) {
        // Flight runs every tick, but only touches Pegasus that actually carry a player.
        flightTask = getServer().getScheduler().runTaskTimer(this, this::tickFlight, 20L, 1L);

        if (config.naturalSpawning().enabled()) {
            long period = Math.max(1L, config.naturalSpawning().checkInterval().toSeconds() * TICKS_PER_SECOND);
            spawnTask = getServer().getScheduler().runTaskTimer(this, this::tickNaturalSpawn, period, period);
            report.ok("Natural spawn system enabled (every "
                    + config.naturalSpawning().checkInterval().toSeconds() + "s, above Y "
                    + config.naturalSpawning().platform().minimumY() + ")");
        } else {
            report.info("Natural spawn system disabled by configuration");
        }

        housekeepingTask = getServer().getScheduler().runTaskTimer(this, this::tickHousekeeping, 1200L, 1200L);
    }

    private void restartTasks() {
        cancel(flightTask);
        cancel(spawnTask);
        cancel(housekeepingTask);
        startTasks(new StartupReport());
    }

    /**
     * Applies flight to every ridden Pegasus.
     *
     * <p>All entity access happens here on the main server thread; nothing in this plugin touches
     * world or entity state asynchronously.
     */
    private void tickFlight() {
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof Horse horse) || !entities.isPegasus(horse)) {
                continue;
            }
            effects.apply(horse, flight.tick(horse, player, now), now);
        }
    }

    private void tickNaturalSpawn() {
        if (!config.naturalSpawning().enabled()) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            NaturalSpawnService.Outcome outcome = naturalSpawn.attemptForPlayer(player, this::debugLine);
            if (outcome.spawned()) {
                debugLine("Pegasus spawned naturally at " + outcome.location());
            }
        }
    }

    /** Periodic cleanup of transient state so uptime does not accumulate stale entries. */
    private void tickHousekeeping() {
        long now = System.currentTimeMillis();
        Set<UUID> live = new HashSet<>();
        getServer().getWorlds().forEach(world ->
                world.getEntities().forEach(entity -> live.add(entity.getUniqueId())));
        flight.purge(live, now);
        breeding.purgeExpired(now);
        pendingEggs.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > PENDING_EGG_TTL);
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    // ------------------------------------------------------------------ egg bridging

    /**
     * Remembers which variant an egg was, keyed by the block position it targeted.
     *
     * <p>{@code PlayerInteractEvent} knows the item but not the resulting entity, while
     * {@code CreatureSpawnEvent} knows the entity but not the item. Entries expire quickly so a
     * cancelled or failed spawn cannot leak or mis-tag a later entity.
     */
    public void rememberPendingEggVariant(Location location, PegasusVariant variant) {
        pendingEggs.put(locationKey(location), new PendingEgg(variant, System.currentTimeMillis()));
    }

    public PegasusVariant consumePendingEggVariant(Location location) {
        long now = System.currentTimeMillis();
        // Check the exact block and its immediate neighbours: the spawn lands near the clicked face.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String key = locationKey(location.clone().add(dx, dy, dz));
                    PendingEgg pending = pendingEggs.get(key);
                    if (pending != null && now - pending.createdAt() <= PENDING_EGG_TTL) {
                        pendingEggs.remove(key);
                        return pending.variant();
                    }
                }
            }
        }
        return null;
    }

    private static String locationKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }

    // ------------------------------------------------------------------ logging

    /** Debug logging is lazy: the message is only built when debug mode is on. */
    public void debug(Supplier<String> message) {
        if (config.debug()) {
            getLogger().info(ansi.info(message.get()));
        }
    }

    private void debugLine(String message) {
        if (config.debug()) {
            getLogger().info(ansi.info(message));
        }
    }

    private void reportResourcePack(StartupReport report) {
        report.info("Resource pack optional: vanilla horse visuals are used unless the "
                + "Pegasus pack is served (see docs/resource-pack.md)");
    }

    // ------------------------------------------------------------------ accessors

    public PegasusConfig config() {
        return config;
    }

    public FlightController flight() {
        return flight;
    }

    public PegasusEffects effects() {
        return effects;
    }

    public PaperOwnershipStore ownership() {
        return ownership;
    }

    public PegasusEntities entities() {
        return entities;
    }
}
