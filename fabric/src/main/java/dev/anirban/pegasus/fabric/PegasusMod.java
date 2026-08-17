/*
 * Pegasus Java Edition — Created by Anirban <3
 *
 * Clean-room Java Edition implementation. No third-party add-on code or assets are used.
 */
package dev.anirban.pegasus.fabric;

import dev.anirban.pegasus.common.Ansi;
import dev.anirban.pegasus.common.BreedingService;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.StartupReport;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric entry point: loads configuration, registers content, and starts server-side systems. */
public final class PegasusMod implements ModInitializer {
    public static final String MOD_ID = "pegasus";
    private static final Logger LOGGER = LoggerFactory.getLogger("Pegasus");

    private static PegasusConfig config = PegasusConfig.defaults();
    private static BreedingService breeding = new BreedingService(config.breeding().preparationWindow());
    private static Ansi ansi = Ansi.autoDetect();

    @Override
    public void onInitialize() {
        StartupReport report = new StartupReport();

        FabricConfig.Loaded loaded = FabricConfig.load();
        config = loaded.config();
        breeding = new BreedingService(config.breeding().preparationWindow());
        ansi = Ansi.autoDetect();
        report.ok("Configuration loaded and validated (" + FabricConfig.path() + ")");
        loaded.warnings().forEach(report::warn);

        PegasusRegistry.registerEntities();
        report.ok("Pegasus entity system registered");
        report.ok("Unicorn breeding system registered");

        PegasusRegistry.registerItems(config);
        report.ok("Spawn eggs registered (" + PegasusVariant.values().length + " variants)");

        PegasusRegistry.registerAttributes();
        report.ok("Entity attributes registered");

        // Ownership lives in each entity's NBT, which vanilla persists with the entity itself.
        report.ok("Ownership storage ready (entity NBT)");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PegasusCommands.register(dispatcher));
        report.ok("Commands registered (/pegasus)");

        NaturalSpawnTicker ticker = new NaturalSpawnTicker();
        ServerTickEvents.END_SERVER_TICK.register(ticker::onEndTick);
        if (config.naturalSpawning().enabled()) {
            report.ok("Natural spawn system enabled (every "
                    + config.naturalSpawning().checkInterval().toSeconds() + "s, above Y "
                    + config.naturalSpawning().platform().minimumY() + ")");
        } else {
            report.info("Natural spawn system disabled by configuration");
        }

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                LOGGER.info(ansi.info("Pegasus shutting down cleanly.")));

        report.info("Client rendering is provided by this mod; no resource pack required on Fabric");
        report.emit(ansi, LOGGER::info);
    }

    public static PegasusConfig config() {
        return config;
    }

    public static BreedingService breeding() {
        return breeding;
    }

    public static Ansi ansi() {
        return ansi;
    }

    public static Logger logger() {
        return LOGGER;
    }

    /** Reloads configuration from disk and returns any validation warnings. */
    public static List<String> reload() {
        FabricConfig.Loaded loaded = FabricConfig.load();
        config = loaded.config();
        breeding = new BreedingService(config.breeding().preparationWindow());
        return loaded.warnings();
    }

    /** Lazy debug logging so disabled debug mode costs nothing. */
    public static void debug(Supplier<String> message) {
        if (config.debug()) {
            LOGGER.info(ansi.info(message.get()));
        }
    }
}
