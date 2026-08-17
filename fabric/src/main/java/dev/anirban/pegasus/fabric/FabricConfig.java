/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.anirban.pegasus.common.Messages;
import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.config.ConfigValidator;
import dev.anirban.pegasus.common.config.MapConfigSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * JSON configuration for Fabric, validated by the same shared {@link ConfigValidator} the Paper
 * module uses so both platforms behave identically for identical settings.
 *
 * <p>Gson is already on the classpath via Minecraft, so no extra dependency is introduced. A missing
 * file is written with documented defaults; an unreadable or malformed file falls back to defaults
 * and reports a warning rather than aborting mod initialisation.
 */
public final class FabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FabricConfig() { }

    public record Loaded(PegasusConfig config, List<String> warnings) { }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("pegasus.json");
    }

    public static Loaded load() {
        Path file = path();
        List<String> warnings = new ArrayList<>();

        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultJson(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                warnings.add("Could not write default config (" + exception.getMessage()
                        + "); continuing with built-in defaults");
            }
            ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(Map.of()));
            warnings.addAll(result.warnings());
            return new Loaded(result.config(), warnings);
        }

        Map<String, Object> flat;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                warnings.add("pegasus.json is not a JSON object; using defaults");
                flat = Map.of();
            } else {
                flat = new LinkedHashMap<>();
                flatten(parsed.getAsJsonObject(), "", flat);
            }
        } catch (IOException | RuntimeException exception) {
            // Covers IO failures and malformed JSON alike; a broken file must not stop the server.
            warnings.add("Could not read pegasus.json (" + exception.getMessage() + "); using defaults");
            flat = Map.of();
        }

        ConfigValidator.Result result = ConfigValidator.validate(new MapConfigSource(flat));
        warnings.addAll(result.warnings());
        return new Loaded(result.config(), warnings);
    }

    /**
     * Flattens nested JSON into the dotted paths the shared validator expects.
     *
     * <p>The {@code messages} object is kept whole because the validator reads it as a map.
     */
    private static void flatten(JsonObject object, String prefix, Map<String, Object> target) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();

            if ("messages".equals(key) && value.isJsonObject()) {
                Map<String, String> messages = new LinkedHashMap<>();
                value.getAsJsonObject().entrySet().forEach(message -> {
                    if (message.getValue().isJsonPrimitive()) {
                        messages.put(message.getKey(), message.getValue().getAsString());
                    }
                });
                target.put(key, messages);
                continue;
            }
            if (value.isJsonObject()) {
                flatten(value.getAsJsonObject(), key, target);
            } else if (value.isJsonArray()) {
                List<String> items = new ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        items.add(element.getAsString());
                    }
                }
                target.put(key, items);
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    target.put(key, primitive.getAsNumber());
                } else if (primitive.isBoolean()) {
                    target.put(key, primitive.getAsBoolean());
                } else {
                    target.put(key, primitive.getAsString());
                }
            }
        }
    }

    /** Serialises the documented defaults, mirroring the Paper {@code config.yml} structure. */
    private static String defaultJson() {
        PegasusConfig defaults = PegasusConfig.defaults();
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Pegasus — Created by Anirban <3. See docs/configuration.md.");
        root.addProperty("debug", defaults.debug());

        JsonObject entity = new JsonObject();
        entity.addProperty("max-health", defaults.entity().maxHealth());
        entity.addProperty("movement-speed", defaults.entity().movementSpeed());
        entity.addProperty("baby-scale", defaults.entity().babyScale());
        root.add("entity", entity);

        JsonObject taming = new JsonObject();
        JsonArray tameItems = new JsonArray();
        defaults.taming().items().forEach(tameItems::add);
        taming.add("items", tameItems);
        taming.addProperty("chance-percent", defaults.taming().chancePercent());
        taming.addProperty("cooldown-seconds", defaults.taming().cooldown().toSeconds());
        taming.addProperty("require-saddle-to-ride", defaults.taming().requireSaddleToRide());
        root.add("taming", taming);

        JsonObject flight = new JsonObject();
        flight.addProperty("horizontal-speed", defaults.flight().horizontalSpeed());
        flight.addProperty("vertical-speed", defaults.flight().verticalSpeed());
        flight.addProperty("sprint-multiplier", defaults.flight().sprintMultiplier());
        flight.addProperty("prevent-fall-damage", defaults.flight().preventFallDamage());
        flight.addProperty("takeoff-cooldown-millis", defaults.flight().takeoffCooldown().toMillis());
        root.add("flight", flight);

        JsonObject spawning = new JsonObject();
        spawning.addProperty("enabled", defaults.naturalSpawning().enabled());
        spawning.addProperty("minimum-y-level", defaults.naturalSpawning().platform().minimumY());
        spawning.addProperty("required-block", "minecraft:hay_block");
        spawning.addProperty("minimum-platform-blocks",
                defaults.naturalSpawning().platform().minimumPlatformBlocks());
        spawning.addProperty("required-block-ratio",
                defaults.naturalSpawning().platform().requiredBlockRatio());
        spawning.addProperty("check-interval-seconds", defaults.naturalSpawning().checkInterval().toSeconds());
        spawning.addProperty("spawn-chance-percent", defaults.naturalSpawning().spawnChancePercent());
        spawning.addProperty("max-nearby-pegasus", defaults.naturalSpawning().maxNearbyPegasus());
        spawning.addProperty("nearby-radius", defaults.naturalSpawning().nearbyRadius());
        JsonArray worlds = new JsonArray();
        worlds.add("minecraft:overworld");
        spawning.add("allowed-worlds", worlds);
        root.add("natural-spawning", spawning);

        JsonObject breedingJson = new JsonObject();
        breedingJson.addProperty("preparation-item", "minecraft:nether_star");
        breedingJson.addProperty("trigger-item", "minecraft:golden_carrot");
        breedingJson.addProperty("preparation-window-seconds", defaults.breeding().preparationWindow().toSeconds());
        breedingJson.addProperty("both-prepared-chance-percent", defaults.breeding().bothPreparedChance());
        breedingJson.addProperty("one-prepared-chance-percent", defaults.breeding().onePreparedChance());
        breedingJson.addProperty("cooldown-seconds", defaults.breedingItems().cooldown().toSeconds());
        root.add("breeding", breedingJson);

        JsonObject eggs = new JsonObject();
        eggs.addProperty("require-permission", defaults.eggs().requirePermission());
        eggs.addProperty("creative-tab", defaults.eggs().creativeTab());
        eggs.addProperty("display-name", defaults.eggs().displayNameFormat());
        JsonArray lore = new JsonArray();
        defaults.eggs().lore().forEach(lore::add);
        eggs.add("lore", lore);
        root.add("spawn-eggs", eggs);

        JsonObject messages = new JsonObject();
        Messages.defaults().forEach(messages::addProperty);
        root.add("messages", messages);

        return GSON.toJson(root);
    }
}
