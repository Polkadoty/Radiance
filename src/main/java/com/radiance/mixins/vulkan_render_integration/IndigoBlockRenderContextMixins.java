package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.compat.SectionModelRenderer;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve Indigo material layers when its single-buffer fallback runs inside Radiance terrain builds. */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderContext", remap = false)
public abstract class IndigoBlockRenderContextMixins {
    @Unique private static final AtomicBoolean radiance$reported = new AtomicBoolean();

    @Inject(method = "getVertexConsumer", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$terrainLayer(RenderLayer layer, CallbackInfoReturnable<VertexConsumer> cir) {
        var buffer = SectionModelRenderer.activeBuffer(layer);
        if (buffer != null) {
            cir.setReturnValue(buffer);
            if (Boolean.getBoolean("radiance.hybridSelfTest") && layer == RenderLayer.getCutout()
                && radiance$reported.compareAndSet(false, true)) {
                System.out.println("[Radiance] Indigo material cutout routed to native PBR terrain buffer");
            }
        }
    }
}
