/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.fabric.PegasusMod;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/** Model layer identifiers for this mod's entities. */
public final class PegasusModelLayers {
    public static final EntityModelLayer PEGASUS =
            new EntityModelLayer(Identifier.of(PegasusMod.MOD_ID, "pegasus"), "main");
    public static final EntityModelLayer UNICORN =
            new EntityModelLayer(Identifier.of(PegasusMod.MOD_ID, "unicorn"), "main");

    private PegasusModelLayers() { }
}
