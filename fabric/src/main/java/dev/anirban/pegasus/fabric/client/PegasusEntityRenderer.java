/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.common.PegasusVariant;
import dev.anirban.pegasus.fabric.PegasusMod;
import dev.anirban.pegasus.fabric.entity.PegasusEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renders the Pegasus.
 *
 * <p>Textures are resolved once into an immutable map at class-init time, so no {@link Identifier}
 * is allocated per frame and a variant can never resolve to a missing texture mid-render, which is
 * what produces the white/black texture flashing some mods show right after spawn.
 */
public class PegasusEntityRenderer extends MobEntityRenderer<PegasusEntity, PegasusEntityModel> {
    private static final Map<PegasusVariant, Identifier> TEXTURES = new EnumMap<>(PegasusVariant.class);

    static {
        for (PegasusVariant variant : PegasusVariant.values()) {
            TEXTURES.put(variant, Identifier.of(PegasusMod.MOD_ID,
                    "textures/entity/pegasus/" + variant.id() + ".png"));
        }
    }

    public PegasusEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PegasusEntityModel(context.getPart(PegasusModelLayers.PEGASUS)), 0.75f);
    }

    @Override
    public Identifier getTexture(PegasusEntity entity) {
        // getOrDefault guarantees a valid texture even if variant data is unexpected.
        return TEXTURES.getOrDefault(entity.variant(), TEXTURES.get(PegasusVariant.CLASSIC));
    }
}
