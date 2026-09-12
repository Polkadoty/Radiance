// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.radiance.hybrid.HybridContext;
import com.radiance.hybrid.OffscreenRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderInstance.class, priority = 900, remap = false)
public abstract class ShaderLifetimeMixin {
    @Inject(method="getId",at=@At("HEAD"),cancellable=true)
    private void hybrid$programId(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> ci) {
        if(HybridContext.ENABLED)ci.setReturnValue(OffscreenRenderer.programId((ShaderInstance)(Object)this));
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void hybrid$close(CallbackInfo ci) {
        if (HybridContext.ENABLED) OffscreenRenderer.release((ShaderInstance)(Object)this);
    }
}
