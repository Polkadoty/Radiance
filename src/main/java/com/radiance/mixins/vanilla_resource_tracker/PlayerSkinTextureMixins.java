package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkinTexture.class)
public class PlayerSkinTextureMixins {
    @Inject(method = "uploadTexture(Lnet/minecraft/client/texture/NativeImage;)V", at = @At("HEAD"))
    private void radiance$trackDownloadedSkin(NativeImage image, CallbackInfo ci) {
        // Downloaded skins bypass ResourceTexture's normal image upload path in 1.21.1.
        int id = ((PlayerSkinTexture) (Object) this).getGlId();
        ((INativeImageExt) (Object) image).radiance$setTargetID(id);
    }
}
