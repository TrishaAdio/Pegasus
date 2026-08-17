/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric;

import dev.anirban.pegasus.common.PegasusConfig;
import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.fabric.entity.PegasusEntity;
import dev.anirban.pegasus.fabric.entity.UnicornEntity;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Central registration of entity types, spawn eggs and default attributes. */
public final class PegasusRegistry {
    /** Wing colour used for the egg overlay; classic is warm, blue-eye is cool. */
    private static final int EGG_BASE_COLOUR = 0xF2E4C4;
    private static final int EGG_CLASSIC_SPOTS = 0xC9A227;
    private static final int EGG_BLUE_SPOTS = 0x3FA9F5;
    private static final int EGG_UNICORN_SPOTS = 0xE7C6FF;

    public static final EntityType<PegasusEntity> PEGASUS = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(PegasusMod.MOD_ID, "pegasus"),
            EntityType.Builder.create(PegasusEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.4f, 1.6f)
                    .eyeHeight(1.52f)
                    .maxTrackingRange(10)
                    .build("pegasus"));

    public static final EntityType<UnicornEntity> UNICORN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(PegasusMod.MOD_ID, "unicorn"),
            EntityType.Builder.create(UnicornEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.4f, 1.6f)
                    .eyeHeight(1.52f)
                    .maxTrackingRange(10)
                    .build("unicorn"));

    private static final Map<PegasusVariant, Item> EGGS = new EnumMap<>(PegasusVariant.class);
    private static Item unicornEgg;

    private PegasusRegistry() { }

    /** Forces class initialisation so the static entity registrations above run. */
    public static void registerEntities() {
        // Touching the fields is sufficient; registration happens in the static initialisers.
        if (PEGASUS == null || UNICORN == null) {
            throw new IllegalStateException("Pegasus entity types failed to register");
        }
    }

    public static void registerAttributes() {
        // Base horse attributes give sane movement, health and jump values to build on.
        FabricDefaultAttributeRegistry.register(PEGASUS, AbstractHorseEntity.createBaseHorseAttributes());
        FabricDefaultAttributeRegistry.register(UNICORN, AbstractHorseEntity.createBaseHorseAttributes());
    }

    /**
     * Registers one spawn egg per Pegasus variant plus a Unicorn egg.
     *
     * <p>Uses vanilla {@link SpawnEggItem}, so vanilla handles placement, NBT and creative
     * behaviour; there is no custom item data that could be malformed at spawn time.
     */
    public static void registerItems(PegasusConfig config) {
        for (PegasusVariant variant : PegasusVariant.values()) {
            int spots = variant == PegasusVariant.BLUE_EYE ? EGG_BLUE_SPOTS : EGG_CLASSIC_SPOTS;
            Item egg = Registry.register(
                    Registries.ITEM,
                    Identifier.of(PegasusMod.MOD_ID, variant.id() + "_pegasus_spawn_egg"),
                    new SpawnEggItem(PEGASUS, EGG_BASE_COLOUR, spots, new Item.Settings()));
            EGGS.put(variant, egg);
        }
        unicornEgg = Registry.register(
                Registries.ITEM,
                Identifier.of(PegasusMod.MOD_ID, "unicorn_spawn_egg"),
                new SpawnEggItem(UNICORN, EGG_BASE_COLOUR, EGG_UNICORN_SPOTS, new Item.Settings()));

        if (config.eggs().creativeTab()) {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
                EGGS.values().forEach(entries::add);
                entries.add(unicornEgg);
            });
        }
    }

    public static Item egg(PegasusVariant variant) {
        return EGGS.get(variant);
    }

    public static Item unicornEgg() {
        return unicornEgg;
    }
}
