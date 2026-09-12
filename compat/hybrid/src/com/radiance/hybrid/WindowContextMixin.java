// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.radiance.hybrid.HybridContext;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Window.class, remap = false)
public abstract class WindowContextMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void hybrid$open(CallbackInfo ci) { HybridContext.open(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void hybrid$close(CallbackInfo ci) { HybridContext.close(); }
}
