package com.radiance.neoforge;

import com.radiance.client.compat.SectionModelRenderer;
import com.radiance.client.compat.SectionGeometryRenderer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.neoforged.neoforge.client.ClientHooks;

/** Mirror NeoForge SectionCompiler's model-data, random-seed and per-layer contract. */
final class NeoForgeSectionModels {
    private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();
    private NeoForgeSectionModels() {}

    static void install() {
        SectionGeometryRenderer.install((origin, world) -> {
            if (!MinecraftClient.getInstance().isOnThread())
                throw new IllegalStateException("Section geometry callbacks must be collected on the client thread");
            var renderers = List.copyOf(ClientHooks.gatherAdditionalRenderers(origin, world));
            if (renderers.isEmpty()) return SectionGeometryRenderer.EMPTY;
            return new SectionGeometryRenderer.Prepared() {
                @Override
                public net.minecraft.client.render.chunk.ChunkRendererRegion createRegion(
                    net.minecraft.client.render.chunk.ChunkRendererRegionBuilder builder,
                    net.minecraft.client.world.ClientWorld level, net.minecraft.util.math.ChunkSectionPos pos) {
                    // A section containing only callback geometry is not empty.
                    return builder.createRegion(level, pos, false);
                }

                @Override
                public void render(net.minecraft.client.render.chunk.ChunkRendererRegion region,
                    net.minecraft.client.util.math.MatrixStack matrices,
                    java.util.function.Function<net.minecraft.client.render.RenderLayer,
                        net.minecraft.client.render.VertexConsumer> buffers) {
                    ClientHooks.addAdditionalGeometry(renderers, buffers, region, matrices);
                }
            };
        });
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
        System.out.println("[Radiance] NeoForge terrain model data, per-model layers and additional section geometry enabled");
    }
}
