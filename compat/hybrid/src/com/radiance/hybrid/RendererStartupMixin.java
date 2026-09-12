// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.radiance.hybrid.HybridContext;
import com.radiance.hybrid.NativeInterop;
import com.radiance.hybrid.RenderTargetSelfTest;
import com.radiance.hybrid.TextureNamespaceSelfTest;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class RendererStartupMixin implements com.radiance.hybrid.HybridMainTargetAccess {
    @Shadow @Final private Window window;
    @Shadow @Final @Mutable private RenderTarget mainRenderTarget;
    @org.spongepowered.asm.mixin.Unique
    public RenderTarget hybrid$swapMainTarget(RenderTarget target) {
        var old=mainRenderTarget;mainRenderTarget=target;return old;
    }
    @Inject(method = "<init>", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V", shift = At.Shift.AFTER))
    private void hybrid$verify(CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        NativeInterop.enableTextureNamespace();
        System.out.println("[Hybrid] OpenGL-owned texture identity allocation active for both APIs");
        if (Boolean.getBoolean("radiance.hybridSelfTest")) {
            if (!NativeInterop.verifyRendererSharing()) throw new IllegalStateException("Renderer sharing test failed");
            System.out.println("[Hybrid] Shared RGBA8/RGBA16F/D32F verification on actual Radiance device PASS");
            RenderTargetSelfTest.run();
            TextureNamespaceSelfTest.run();
            com.radiance.hybrid.StencilRestoreSelfTest.run();
            int previousMaterialBinding = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            try { com.radiance.client.texture.MaterialCacheSelfTest.run(); }
            finally { com.mojang.blaze3d.platform.GlStateManager._bindTexture(previousMaterialBinding); }
            com.radiance.hybrid.OffscreenDrawSelfTest.run();
            com.radiance.hybrid.HudTransferSelfTest.run();
        }
        // Radiance suppresses vanilla's main-target assignment. Supply a real GL
        // target for mod consumers; Vulkan still owns presentation and scene draws.
        mainRenderTarget = new MainTarget(Math.max(1, window.getWidth()), Math.max(1, window.getHeight()));
        com.radiance.hybrid.HybridTargets.registerPresentationTarget(mainRenderTarget);
        mainRenderTarget.setClearColor(0, 0, 0, 0);
        mainRenderTarget.clear(false);
        if (Boolean.getBoolean("radiance.hybridSelfTest"))
            com.radiance.hybrid.TargetRoutingSelfTest.run(mainRenderTarget, target -> mainRenderTarget = target);
        System.out.println("[Hybrid] Main render target allocated; GPU HUD composition enabled, world GL composition pending");
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void hybrid$resize(CallbackInfo ci) {
        if (HybridContext.ENABLED)
            com.radiance.hybrid.HybridTargets.resize(Math.max(1, window.getWidth()), Math.max(1, window.getHeight()));
    }

    @org.spongepowered.asm.mixin.Unique private boolean hybrid$resourcesClosed;
    // Radiance closes Vulkan at Minecraft.stop() TAIL. Minecraft.close() runs
    // later, when destroying shared HUD images would use an invalid VkDevice.
    @Inject(method = {"stop", "close"}, at = @At("HEAD"))
    private void hybrid$close(CallbackInfo ci) {
        if (HybridContext.ENABLED && !hybrid$resourcesClosed) {
            com.radiance.hybrid.HudCompositor.close();
            com.radiance.hybrid.HybridTargets.destroy();
            hybrid$resourcesClosed=true;
            System.out.println("[Hybrid] Shared HUD resources released before native renderer shutdown");
        }
    }
}
