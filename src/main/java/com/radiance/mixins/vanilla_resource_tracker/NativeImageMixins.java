package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.IdentifierInputStream;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.InputStream;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NativeImage.class)
public abstract class NativeImageMixins implements INativeImageExt {

    @Unique
    private int targetID = -1;

    @Unique
    private Identifier identifier = null;

    @Unique
    private NativeImage specularImage = null;

    @Unique
    private NativeImage normalImage = null;

    @Unique
    private NativeImage flagImage = null;

    @Unique private Identifier radiance$auxiliaryIdentifier;
    @Unique private int radiance$auxiliaryLevel = -1;
    @Unique private long radiance$auxiliaryGeneration = -1;

    @Override
    public void radiance$prepareAuxiliaryImages(Identifier id, int level, long generation) {
        if (generation == radiance$auxiliaryGeneration && level == radiance$auxiliaryLevel
            && java.util.Objects.equals(id, radiance$auxiliaryIdentifier)) return;
        if (specularImage != null) { specularImage.close(); specularImage = null; }
        if (normalImage != null) { normalImage.close(); normalImage = null; }
        if (flagImage != null) { flagImage.close(); flagImage = null; }
        radiance$auxiliaryIdentifier = id;
        radiance$auxiliaryLevel = level;
        radiance$auxiliaryGeneration = generation;
    }

    @Inject(method = "read(Lnet/minecraft/client/texture/NativeImage$Format;Ljava/io/InputStream;)"
        +
        "Lnet/minecraft/client/texture/NativeImage;", at = @At(value = "RETURN"), cancellable = true)
    private static void readIdentifier(NativeImage.Format format, InputStream stream,
        CallbackInfoReturnable<NativeImage> cir) {
        NativeImage nativeImage = cir.getReturnValue();

        if (stream instanceof IdentifierInputStream) {
            Identifier identifier = ((IdentifierInputStream) stream).getResourceId();
            ((INativeImageExt) (Object) nativeImage).radiance$setIdentifier(identifier);
            cir.setReturnValue(nativeImage);
        } else {
            cir.setReturnValue(nativeImage);
        }
    }

    @Override
    public int radiance$getTargetID() {
        return targetID;
    }

    @Override
    public void radiance$setTargetID(int id) {
        this.targetID = id;
    }

    @Override
    public Identifier radiance$getIdentifier() {
        return identifier;
    }

    @Override
    public void radiance$setIdentifier(Identifier id) {
        this.identifier = id;
    }

    @Override
    public NativeImage radiance$getSpecularNativeImage() {
        return specularImage;
    }

    @Override
    public void radiance$setSpecularNativeImage(NativeImage image) {
        this.specularImage = image;
    }

    @Override
    public NativeImage radiance$getNormalNativeImage() {
        return normalImage;
    }

    @Override
    public void radiance$setNormalNativeImage(NativeImage image) {
        this.normalImage = image;
    }

    @Override
    public NativeImage radiance$getFlagNativeImage() {
        return flagImage;
    }

    @Override
    public void radiance$setFlagNativeImage(NativeImage image) {
        this.flagImage = image;
    }
}
