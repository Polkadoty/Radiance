package com.radiance.compat.sable.callbacks.mixin;

import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary.Feature.WATER_SURFACE_OCCLUSION;

@Mixin(value=WaterOcclusionRenderer.class, remap=false)
public abstract class SableWaterEffectsMixin {
    @Inject(method="isEnabled()Z", at=@At("HEAD"), cancellable=true)
    private static void radiance$disabled(CallbackInfoReturnable<Boolean> cir) {
        if (SableRenderFeatureBoundary.enabled()) cir.setReturnValue(false);
    }
    @ModifyVariable(method="setIsEnabled(Z)V", at=@At("HEAD"), argsOnly=true, ordinal=0)
    private static boolean radiance$requested(boolean requested) {
        return SableRenderFeatureBoundary.effectiveEnabled(requested, WATER_SURFACE_OCCLUSION);
    }
    @Inject(method={"preRenderTranslucent(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", "setupTranslucentShader(Lnet/minecraft/client/renderer/ShaderInstance;)V", "updateFramebuffers(Z)V"}, at=@At("HEAD"), cancellable=true)
    private void radiance$skipEffect(CallbackInfo ci) {
        if (SableRenderFeatureBoundary.enabled()) { SableRenderFeatureBoundary.omit(WATER_SURFACE_OCCLUSION); ci.cancel(); }
    }
    // addRegion/removeRegion/update and all WaterOcclusionContainer CPU queries remain intact.
}
