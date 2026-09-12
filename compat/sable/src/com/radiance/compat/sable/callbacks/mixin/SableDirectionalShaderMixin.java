package com.radiance.compat.sable.callbacks.mixin;

import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary.Feature.DIRECTIONAL_SHADER;

@Mixin(value=SableDynamicDirectionalShading.class, remap=false)
public abstract class SableDirectionalShaderMixin {
    @Inject(method="isEnabled()Z", at=@At("HEAD"), cancellable=true)
    private static void radiance$disabled(CallbackInfoReturnable<Boolean> cir) {
        if (SableRenderFeatureBoundary.enabled()) cir.setReturnValue(false);
    }
    @ModifyVariable(method="setIsEnabled(Z)V", at=@At("HEAD"), argsOnly=true, ordinal=0)
    private static boolean radiance$requested(boolean requested) {
        return SableRenderFeatureBoundary.effectiveEnabled(requested, DIRECTIONAL_SHADER);
    }
}
