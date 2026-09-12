// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.hybrid.HybridContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, priority = 1100, remap = false)
public abstract class TextureBindingMixin {
    @Shadow private static int activeTexture;

    @Inject(method = "_activeTexture", at = @At("HEAD"), cancellable = true)
    private static void hybrid$active(int texture, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        GL13.glActiveTexture(texture);
        activeTexture = texture - GL13.GL_TEXTURE0;
        ci.cancel();
    }

    @Inject(method = "_bindTexture", at = @At("HEAD"))
    private static void hybrid$bind(int texture, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        // Raw mod GL calls and framebuffer allocation can bypass Mojang's cache.
        // Bind explicitly, then let vanilla update its cached texture identity.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }
}
