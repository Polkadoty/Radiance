package com.radiance.compat.dh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.seibel.distanthorizons.neoforge.NeoforgeClientProxy", remap = false)
public abstract class NeoforgeClientProxyMixin {
    // In pinned DH 3.2.0/MC 1.21.1 this event only captures GL_FRAMEBUFFER_BINDING for Optifine.
    // Radiance has no GL context; its explicit callback already owns DH's frame pump.
    @Inject(method = "afterLevelRenderEvent(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$skipGlFramebufferCapture(CallbackInfo ci) { ci.cancel(); }
}
