/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.paper;

import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Identity and attribute helpers for Paper Pegasus and Unicorn entities.
 *
 * <p>Both are vanilla {@link Horse}s carrying persistent marker data rather than bespoke entity
 * types. Paper's public API cannot register a new entity type, and this approach deliberately
 * inherits vanilla taming, saddling, mounting, breeding and pathfinding — all of which are
 * well-tested and correctly synchronised to clients — instead of reimplementing them with NMS.
 */
public final class PegasusEntities {
    private final PegasusKeys keys;

    public PegasusEntities(PegasusKeys keys) {
        this.keys = keys;
    }

    public boolean isPegasus(Entity entity) {
        return entity instanceof Horse horse
                && PegasusKeys.hasFlag(horse.getPersistentDataContainer(), keys.pegasusMarker());
    }

    public boolean isUnicorn(Entity entity) {
        return entity instanceof Horse horse
                && PegasusKeys.hasFlag(horse.getPersistentDataContainer(), keys.unicornMarker());
    }

    public PegasusVariant variantOf(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return PegasusVariant.CLASSIC;
        }
        String raw = living.getPersistentDataContainer().get(keys.variant(), PersistentDataType.STRING);
        return PegasusVariant.parseOrDefault(raw);
    }

    /** Marks a freshly spawned horse as a Pegasus and applies configured attributes. */
    public void initialisePegasus(Horse horse, PegasusVariant variant, PegasusConfig config, boolean baby) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        PegasusKeys.setFlag(data, keys.pegasusMarker());
        data.set(keys.variant(), PersistentDataType.STRING, variant.id());
        data.set(keys.dataVersion(), PersistentDataType.INTEGER, 1);

        applyAttributes(horse, config);
        horse.setTamed(false);
        horse.setOwner(null);
        horse.setAdult();
        if (baby) {
            horse.setBaby();
        }
        // Jump strength drives vanilla horse jumping; flight is handled by the flight controller.
        horse.setJumpStrength(Math.min(2.0, Math.max(0.0, 0.9)));
        // Adventure component API; setCustomName(String) is deprecated on Paper.
        horse.customName(Component.text(variant.displayName()));
        horse.setCustomNameVisible(false);
        horse.setPersistent(true);
        horse.setRemoveWhenFarAway(false);
    }

    public void initialiseUnicorn(Horse horse, PegasusConfig config) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        PegasusKeys.setFlag(data, keys.unicornMarker());
        data.set(keys.dataVersion(), PersistentDataType.INTEGER, 1);
        applyAttributes(horse, config);
        horse.customName(Component.text("Unicorn"));
        horse.setCustomNameVisible(false);
        horse.setPersistent(true);
        horse.setRemoveWhenFarAway(false);
    }

    /**
     * Applies health and speed. Health is set before {@code setHealth} so the entity never sits in
     * an invalid state where current health exceeds its maximum.
     */
    public void applyAttributes(AbstractHorse horse, PegasusConfig config) {
        // 1.21.1 exposes these as GENERIC_* enum constants.
        attribute(horse, Attribute.GENERIC_MAX_HEALTH).ifPresent(instance -> {
            instance.setBaseValue(config.entity().maxHealth());
            // Clamp to the new maximum so the entity never holds health above its cap.
            horse.setHealth(Math.min(Math.max(horse.getHealth(), 1.0), instance.getValue()));
        });
        attribute(horse, Attribute.GENERIC_MOVEMENT_SPEED)
                .ifPresent(instance -> instance.setBaseValue(config.entity().movementSpeed()));
    }

    /** Attribute lookups can legitimately return null for an entity type; never assume presence. */
    private static Optional<AttributeInstance> attribute(LivingEntity entity, Attribute attribute) {
        return Optional.ofNullable(entity.getAttribute(attribute));
    }

    public NamespacedKey pegasusMarkerKey() {
        return keys.pegasusMarker();
    }
}
