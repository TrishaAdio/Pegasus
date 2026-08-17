/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.fabric.PegasusMod;
import dev.anirban.pegasus.fabric.entity.UnicornEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renders the Unicorn using the same original geometry as the Pegasus.
 *
 * <p>The shared model keeps the two visually related; the Unicorn texture simply omits feathering
 * and adds a horn, and its wings stay folded because a Unicorn never enters a flight state.
 */
public class UnicornEntityRenderer extends MobEntityRenderer<UnicornEntity, UnicornEntityModel> {
    private static final Identifier TEXTURE =
            Identifier.of(PegasusMod.MOD_ID, "textures/entity/unicorn/unicorn.png");

    public UnicornEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new UnicornEntityModel(context.getPart(PegasusModelLayers.UNICORN)), 0.75f);
    }

    @Override
    public Identifier getTexture(UnicornEntity entity) {
        return TEXTURE;
    }
}
