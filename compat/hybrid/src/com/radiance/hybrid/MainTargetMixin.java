// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.radiance.hybrid.HybridContext;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MainTarget's specialized constructor must use the same attachment allocator. */
@Mixin(value = MainTarget.class, remap = false)
public abstract class MainTargetMixin extends RenderTarget {
    protected MainTargetMixin(boolean depth) { super(depth); }

    @Inject(method = "createFrameBuffer", at = @At("HEAD"), cancellable = true)
    private void hybrid$create(int width, int height, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        createBuffers(width, height, false);
        ci.cancel();
    }
}
