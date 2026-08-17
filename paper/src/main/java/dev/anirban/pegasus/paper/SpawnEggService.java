/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.common.SpawnEggData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds and reads Pegasus spawn eggs.
 *
 * <p>Eggs are vanilla horse spawn eggs carrying persistent variant data, so they stack, render and
 * behave normally in every inventory. The egg's variant is validated on use rather than trusted:
 * hand-edited or third-party data falls back to {@code CLASSIC} instead of throwing while spawning.
 */
public final class SpawnEggService {
    private static final Material EGG_MATERIAL = Material.HORSE_SPAWN_EGG;

    private final PegasusKeys keys;
    private volatile PegasusConfig config;

    public SpawnEggService(PegasusKeys keys, PegasusConfig config) {
        this.keys = keys;
        this.config = config;
    }

    public void updateConfig(PegasusConfig config) {
        this.config = config;
    }

    public ItemStack create(PegasusVariant variant, int amount) {
        ItemStack item = new ItemStack(EGG_MATERIAL, Math.max(1, Math.min(amount, EGG_MATERIAL.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Defensive: a null meta would otherwise NPE. Return a plain egg rather than crash.
            return item;
        }

        meta.displayName(Component.text(renderName(variant))
                .color(variant == PegasusVariant.BLUE_EYE ? NamedTextColor.AQUA : NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : config.eggs().lore()) {
            lore.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(keys.eggVariant(), PersistentDataType.STRING, variant.id());
        data.set(keys.eggVersion(), PersistentDataType.INTEGER, SpawnEggData.CURRENT_VERSION);
        item.setItemMeta(meta);
        return item;
    }

    private String renderName(PegasusVariant variant) {
        return config.eggs().displayNameFormat().replace("{variant}", variant.displayName());
    }

    /** True when this stack is one of our eggs (as opposed to a vanilla horse egg). */
    public boolean isPegasusEgg(ItemStack item) {
        return readVariant(item).isPresent();
    }

    /**
     * Reads the variant from an item.
     *
     * @return empty when the item is not a Pegasus egg at all
     */
    public Optional<PegasusVariant> readVariant(ItemStack item) {
        if (item == null || item.getType() != EGG_MATERIAL || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String raw = data.get(keys.eggVariant(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        Map<String, String> stored = new HashMap<>();
        stored.put(SpawnEggData.VARIANT_KEY, raw);
        // deserializeOrDefault guarantees a usable variant even if the stored value is junk.
        return Optional.of(SpawnEggData.deserializeOrDefault(stored).variant());
    }

    public static Material eggMaterial() {
        return EGG_MATERIAL;
    }

    /** Permission node for a specific variant, used when {@code spawn-eggs.require-permission} is on. */
    public static String permissionFor(PegasusVariant variant) {
        return "pegasus.egg." + variant.id();
    }
}
