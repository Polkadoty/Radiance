// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.radiance.hybrid.HybridContext;
import foundry.veil.forge.impl.ForgeRenderTypeStageHandler;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderType.class, priority = 500, remap = false)
public abstract class VeilLayerListMixin {
    @Inject(method = "chunkBufferLayers", at = @At("RETURN"), cancellable = true)
    private static void hybrid$layers(CallbackInfoReturnable<List<RenderType>> ci) {
        if (!HybridContext.ENABLED || !"radiance".equals(System.getProperty("veil.backend"))) return;
        ci.setReturnValue(ForgeRenderTypeStageHandler.getBlockLayers(ci.getReturnValue()));
    }
}
