// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.radiance.hybrid.HybridContext;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Veil 4.3.2 capability delegates only read immutable feature booleans. */
@Mixin(targets = "foundry.veil.api.client.render.VeilRenderSystem", remap = false)
public abstract class VeilCapabilitiesMixin {
    @Inject(method = "glCapability", at = @At("HEAD"), cancellable = true)
    private static void hybrid$capability(Function<GLCapabilities, Boolean> query,
            CallbackInfoReturnable<BooleanSupplier> ci) {
        if (!HybridContext.ENABLED) return;
        // Lazy: Veil can initialize its class before the window exists. No GL call,
        // context migration, or render-thread assertion occurs on a worker thread.
        ci.setReturnValue(() -> query.apply(HybridContext.capabilitiesSnapshot()));
    }
}
