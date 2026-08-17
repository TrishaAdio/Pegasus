/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of {@link NamespacedKey}s written into entity and item
 * {@link PersistentDataContainer}s.
 *
 * <p>PDC is the stable Paper mechanism for durable custom data: it is saved with the entity, so it
 * survives chunk unload, entity reload, world save and restart without any extra bookkeeping.
 */
public final class PegasusKeys {
    private final NamespacedKey pegasusMarker;
    private final NamespacedKey unicornMarker;
    private final NamespacedKey variant;
    private final NamespacedKey ownerUuid;
    private final NamespacedKey ownerName;
    private final NamespacedKey dataVersion;
    private final NamespacedKey eggVariant;
    private final NamespacedKey eggVersion;

    public PegasusKeys(Plugin plugin) {
        this.pegasusMarker = new NamespacedKey(plugin, "is_pegasus");
        this.unicornMarker = new NamespacedKey(plugin, "is_unicorn");
        this.variant = new NamespacedKey(plugin, "variant");
        this.ownerUuid = new NamespacedKey(plugin, "owner_uuid");
        this.ownerName = new NamespacedKey(plugin, "owner_name");
        this.dataVersion = new NamespacedKey(plugin, "data_version");
        this.eggVariant = new NamespacedKey(plugin, "egg_variant");
        this.eggVersion = new NamespacedKey(plugin, "egg_version");
    }

    public NamespacedKey pegasusMarker() {
        return pegasusMarker;
    }

    public NamespacedKey unicornMarker() {
        return unicornMarker;
    }

    public NamespacedKey variant() {
        return variant;
    }

    public NamespacedKey ownerUuid() {
        return ownerUuid;
    }

    public NamespacedKey ownerName() {
        return ownerName;
    }

    public NamespacedKey dataVersion() {
        return dataVersion;
    }

    public NamespacedKey eggVariant() {
        return eggVariant;
    }

    public NamespacedKey eggVersion() {
        return eggVersion;
    }

    /** Reads a byte flag defensively; absent or non-byte data is treated as "not set". */
    public static boolean hasFlag(PersistentDataContainer container, NamespacedKey key) {
        Byte value = container.get(key, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public static void setFlag(PersistentDataContainer container, NamespacedKey key) {
        container.set(key, PersistentDataType.BYTE, (byte) 1);
    }
}
