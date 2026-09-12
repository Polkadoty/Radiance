package com.radiance.compat.dh.mixin;
import com.radiance.compat.dh.RadianceDhRenderApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.DependencySetup_neoforge", remap = false)
public abstract class DependencySetupMixin {
    @Shadow private static boolean renderingApiBindingsSet;
    @Inject(method = "setRenderingApiBindings", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radiance$selectBackend(CallbackInfo ci) {
        if (!renderingApiBindingsSet) { RadianceDhRenderApi.install(); renderingApiBindingsSet = true; }
        ci.cancel();
    }
}
