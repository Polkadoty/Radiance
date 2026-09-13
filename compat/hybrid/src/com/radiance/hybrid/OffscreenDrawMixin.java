// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.hybrid.OffscreenRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BufferUploader.class, priority = 1100, remap = false)
public abstract class OffscreenDrawMixin {
    // Dispatch before the private method that Radiance replaces. Its cancelling
    // injector can run before another HEAD injector in that same method.
    // Preserve vanilla queueing for callers outside the render thread; the
    // queued lambda reaches the same decision once on the owning thread.
    @Inject(method = {"drawWithShader", "lambda$drawWithShader$0"}, at = @At("HEAD"), cancellable = true)
    private static void hybrid$draw(MeshData mesh, CallbackInfo ci) {
        if (!RenderSystem.isOnRenderThread()) return;
        com.radiance.hybrid.NativeHudCompositor.beforeDraw(mesh,false);
        if (!OffscreenRenderer.isPrivateTarget()) {
            com.radiance.hybrid.HudCompositor.recordHudDraw(true);
            return;
        }
        com.radiance.hybrid.HudCompositor.recordHudDraw(false);
        try { OffscreenRenderer.draw(mesh); }
        finally { mesh.close(); }
        ci.cancel();
    }
    @Inject(method="draw",at=@At("HEAD"),cancellable=true)
    private static void hybrid$drawBound(MeshData mesh,CallbackInfo ci) {
        if(RenderSystem.isOnRenderThread())com.radiance.hybrid.NativeHudCompositor.beforeDraw(mesh,true);
        if(!RenderSystem.isOnRenderThread()||!OffscreenRenderer.isPrivateTarget())return;
        com.radiance.hybrid.HudCompositor.recordHudDraw(false);
        try{OffscreenRenderer.drawBound(mesh);}finally{mesh.close();}ci.cancel();
    }
}
