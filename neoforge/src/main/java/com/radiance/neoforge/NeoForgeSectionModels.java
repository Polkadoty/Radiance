package com.radiance.neoforge;

import com.radiance.client.compat.SectionModelRenderer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Mirror NeoForge SectionCompiler's model-data, random-seed and per-layer contract. */
final class NeoForgeSectionModels {
    private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();
    private NeoForgeSectionModels() {}

    static void install() {
        SectionModelRenderer.install((manager, state, pos, region, matrices, random, buffers) -> {
            var model = manager.getModel(state);
            var modelData = model.getModelData(region, pos, state, region.getModelData(pos));
            random.setSeed(state.getRenderingSeed(pos));
            var layers = model.getRenderTypes(state, random, modelData);
            for (var layer : layers) {
                if (Boolean.getBoolean("radiance.hybridSelfTest") && layer != RenderLayers.getBlockLayer(state)) {
                    var id = Registries.BLOCK.getId(state.getBlock());
                    if (REPORTED.add(id)) System.out.println("[Radiance] NeoForge model layer restored: " + id
                        + " vanilla=" + RenderLayers.getBlockLayer(state) + " model=" + layer);
                }
                manager.renderBatched(state, pos, region, matrices, buffers.apply(layer), true, random, modelData, layer);
            }
        });
        System.out.println("[Radiance] NeoForge terrain model data and per-model render layers enabled");
    }
}
