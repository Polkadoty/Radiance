package com.radiance.compat.sable.host.mixin;

import com.radiance.compat.sable.host.SableVulkanDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Create prototype hook called by Radiance's cancelled vanilla world renderer. */
@Mixin(targets="com.radiance.client.compat.SyntheticPersistentScene",remap=false)
public abstract class RadianceFrameMixin {
    @Inject(method="render",at=@At("HEAD"))
    private static void radiance$frame(Minecraft client,Camera camera,CallbackInfo ci) {
        SableVulkanDispatcher.get().frame(client);
    }
}
