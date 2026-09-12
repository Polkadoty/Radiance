package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public interface INativeImageExt {

    int radiance$getTargetID();

    void radiance$setTargetID(int id);

    Identifier radiance$getIdentifier();

    void radiance$setIdentifier(Identifier id);

    /** Cache lifetime follows the owning source image; reloads and mip/source changes invalidate it. */
    void radiance$prepareAuxiliaryImages(Identifier id, int level, long generation);

    NativeImage radiance$getSpecularNativeImage();

    void radiance$setSpecularNativeImage(NativeImage image);

    NativeImage radiance$getNormalNativeImage();

    void radiance$setNormalNativeImage(NativeImage image);

    NativeImage radiance$getFlagNativeImage();

    void radiance$setFlagNativeImage(NativeImage image);
}
