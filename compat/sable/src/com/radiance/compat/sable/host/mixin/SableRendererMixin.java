package com.radiance.compat.sable.host.mixin;

import com.radiance.compat.sable.host.SableVulkanDispatcher;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderer;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=SubLevelRenderer.class, remap=false)
public abstract class SableRendererMixin {
    @Inject(method="getDispatcher",at=@At("HEAD"),cancellable=true)
    private static void radiance$get(CallbackInfoReturnable<SubLevelRenderDispatcher> cir) {
        cir.setReturnValue(SableVulkanDispatcher.get());
    }
    @Inject(method="setImpl",at=@At("HEAD"),cancellable=true)
    private static void radiance$select(SubLevelRenderer.SelectedRenderer selected,CallbackInfo ci) {
        // The explicit Vulkan backend owns selection for this test session; preserve saved preference.
        ci.cancel();
    }
    @Inject(method="free",at=@At("HEAD"),cancellable=true)
    private static void radiance$free(CallbackInfo ci) { SableVulkanDispatcher.get().free(); ci.cancel(); }
}
