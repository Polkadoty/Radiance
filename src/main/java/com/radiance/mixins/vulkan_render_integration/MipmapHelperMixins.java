package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.MipmapHelper;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MipmapHelper.class)
public class MipmapHelperMixins {
    @Inject(method = "getMipmapLevelsImages", at = @At("RETURN"))
    private static void radiance$identifyMipLevels(NativeImage[] originals, int mipmap,
            CallbackInfoReturnable<NativeImage[]> cir) {
        var identifier = ((INativeImageExt) (Object) originals[0]).radiance$getIdentifier();
        for (NativeImage image : cir.getReturnValue()) {
            ((INativeImageExt) (Object) image).radiance$setIdentifier(identifier);
        }
    }
}