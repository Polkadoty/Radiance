package com.radiance.client.compat;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** Capture loader callbacks with the region snapshot, before dispatching worker work. */
public final class SectionGeometryRenderer {
    @FunctionalInterface
    public interface Prepared {
        void render(ChunkRendererRegion region, MatrixStack matrices,
            Function<RenderLayer, VertexConsumer> buffers);

        default ChunkRendererRegion createRegion(ChunkRendererRegionBuilder builder,
            ClientWorld world, ChunkSectionPos pos) {
            return builder.build(world, pos);
        }
    }

    public static final Prepared EMPTY = (region, matrices, buffers) -> {};
    private static volatile BiFunction<BlockPos, ClientWorld, Prepared> collector = (pos, world) -> EMPTY;

    private SectionGeometryRenderer() {}

    public static void install(BiFunction<BlockPos, ClientWorld, Prepared> loaderCollector) {
        collector = Objects.requireNonNull(loaderCollector);
    }

    /** Must be called on the client thread with the origin of this particular build. */
    public static Prepared capture(BlockPos origin, ClientWorld world) {
        return Objects.requireNonNull(collector.apply(origin.toImmutable(), world));
    }
}
