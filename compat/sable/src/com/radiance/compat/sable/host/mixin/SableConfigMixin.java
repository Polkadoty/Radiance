package com.radiance.compat.sable.host.mixin;

import com.radiance.compat.sable.host.SableVulkanDispatcher;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SableClientConfig.class,remap=false)
public abstract class SableConfigMixin {
    @Inject(method="onUpdate",at=@At("HEAD"),cancellable=true)
    private static void radiance$config(boolean notify,CallbackInfo ci) {
        // Native rays own directional lighting/shadows. Never ask Veil to reload a GL shader.
        SableSkyLightShadows.setIsEnabled(false);
        if (notify) Minecraft.getInstance().execute(() ->
            SableVulkanDispatcher.get().onResourceManagerReload(Minecraft.getInstance().getResourceManager()));
        ci.cancel();
    }
}
