package com.radiance.client.compat;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Loader hook for model data and layered terrain; the common path stays vanilla-compatible. */
public final class SectionModelRenderer {
    @FunctionalInterface
    public interface Renderer {
        void render(BlockRenderManager manager, BlockState state, BlockPos pos,
            ChunkRendererRegion region, MatrixStack matrices, Random random,
            Function<RenderLayer, VertexConsumer> buffers);
    }

    private static volatile Renderer renderer = (manager, state, pos, region, matrices, random, buffers) ->
        manager.renderBlock(state, pos, region, matrices, buffers.apply(RenderLayers.getBlockLayer(state)), true, random);

    private SectionModelRenderer() {}

    public static void install(Renderer loaderRenderer) { renderer = Objects.requireNonNull(loaderRenderer); }

    public static void render(BlockRenderManager manager, BlockState state, BlockPos pos,
        ChunkRendererRegion region, MatrixStack matrices, Random random,
        Function<RenderLayer, VertexConsumer> buffers) {
        renderer.render(manager, state, pos, region, matrices, random, buffers);
    }
}
