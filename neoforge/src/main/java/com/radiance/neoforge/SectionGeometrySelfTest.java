package com.radiance.neoforge;

import com.radiance.client.vertex.PBRVertexConsumer;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in integration probe: exercise the real event/worker/buffer path without adding world geometry. */
final class SectionGeometrySelfTest {
    private static final AtomicInteger collected = new AtomicInteger();
    private static final AtomicInteger completed = new AtomicInteger();
    private SectionGeometrySelfTest() {}

    static int completed() { return completed.get(); }
    static void reset() { collected.set(0); completed.set(0); }

    static void install() {
        if (Boolean.getBoolean("radiance.hybridSelfTest"))
            NeoForge.EVENT_BUS.addListener(SectionGeometrySelfTest::collect);
    }

    private static void collect(AddSectionGeometryEvent event) {
        if (collected.get() >= 3) return;
        var client = MinecraftClient.getInstance();
        require(client.isOnThread(), "event ran off the client thread");
        if (client.player == null || event.getLevel() != client.world) return;
        var origin = event.getSectionOrigin().toImmutable();
        // Restrict the probe to nearby sections. It creates no vertices or blocks.
        if (origin.getSquaredDistance(client.player.getBlockPos()) > 32 * 32) return;
        collected.incrementAndGet();
        Thread captureThread = Thread.currentThread();
        event.addRenderer(context -> {
            require(Thread.currentThread() != captureThread, "terrain callback did not reach a rebuild worker");
            require(context.getRegion() != null, "missing captured region");
            require(context.getPoseStack().peek().getPositionMatrix().equals(new org.joml.Matrix4f()),
                "callback did not start at section-local identity pose");
            for (var layer : new RenderLayer[]{RenderLayer.getSolid(), RenderLayer.getCutout(), RenderLayer.getTranslucent()}) {
                var buffer = context.getOrCreateChunkBuffer(layer);
                require(buffer instanceof PBRVertexConsumer, "callback bypassed Radiance PBR geometry");
                require(buffer == context.getOrCreateChunkBuffer(layer), "layer buffer ownership changed");
            }
            int count = completed.incrementAndGet();
            System.out.println("[Radiance] Section geometry event/client capture/worker callback/PBR layer buffers PASS "
                + count + "/3 at " + origin);
        });
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException("Section geometry self-test: " + message);
    }
}
