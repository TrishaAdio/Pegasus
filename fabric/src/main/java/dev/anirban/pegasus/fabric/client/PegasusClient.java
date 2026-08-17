/*
 * Pegasus Java Edition — Created by Anirban <3
 */
package dev.anirban.pegasus.fabric.client;

import dev.anirban.pegasus.fabric.PegasusRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client entry point.
 *
 * <p>Model layers are registered before renderers so the geometry is guaranteed to exist the first
 * time an entity is drawn. This ordering is what prevents a missing-model exception or a one-frame
 * invisible entity immediately after the first spawn.
 */
public final class PegasusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(PegasusModelLayers.PEGASUS,
                PegasusEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PegasusModelLayers.UNICORN,
                UnicornEntityModel::getTexturedModelData);

        EntityRendererRegistry.register(PegasusRegistry.PEGASUS, PegasusEntityRenderer::new);
        EntityRendererRegistry.register(PegasusRegistry.UNICORN, UnicornEntityRenderer::new);
    }
}
