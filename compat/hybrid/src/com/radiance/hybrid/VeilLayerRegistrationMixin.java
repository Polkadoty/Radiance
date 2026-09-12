// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.radiance.hybrid.HybridContext;
import foundry.veil.forge.event.ForgeVeilRegisterBlockLayersEvent;
import foundry.veil.forge.impl.ForgeRenderTypeStageHandler;
import java.util.LinkedHashSet;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.fml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restore CPU registration omitted by the earlier Veil Radiance boundary. */
@Mixin(value = RenderBuffers.class, remap = false)
public abstract class VeilLayerRegistrationMixin {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void hybrid$register(CallbackInfo ci) {
        if (!HybridContext.ENABLED || !"radiance".equals(System.getProperty("veil.backend"))) return;
        var layers = new LinkedHashSet<RenderType>();
        ModLoader.postEvent(new ForgeVeilRegisterBlockLayersEvent(layers::add));
        ForgeRenderTypeStageHandler.setBlockLayers(layers);
        System.out.println("[Hybrid] Registered " + layers.size() + " custom chunk layers; GPU routing remains pending");
    }
}
