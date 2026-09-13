package com.radiance.client.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/** Startup-only checks using the actual transformed NativeImage and native texture uploader. */
public final class MaterialCacheSelfTest {
    private MaterialCacheSelfTest() {}
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Material cache self-test: " + message);
    }
    private static long pointer(NativeImage image) {
        return ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)(Object)image).radiance$getPointer();
    }
    private static NativeImage[] maps(INativeImageExt ext) {
        return new NativeImage[]{ext.radiance$getSpecularNativeImage(), ext.radiance$getNormalNativeImage(), ext.radiance$getFlagNativeImage()};
    }
    public static void run() {
        RenderSystem.assertOnRenderThread();
        ConnectedMaterialSelfTest.run();
        int texture = TextureUtil.generateTextureId();
        NativeImage source = new NativeImage(NativeImage.Format.RGBA,16,16,true);
        INativeImageExt ext = (INativeImageExt)(Object)source;
        Identifier id = Identifier.of("radiance", "textures/block/material_cache_self_test.png");
        try {
            TextureUtil.prepareImage(texture,1,32,32);
            ext.radiance$setTargetID(texture);
            ext.radiance$setIdentifier(id);
            AuxiliaryTextures.loadAndUpload(source,ext,0,0,0,0,0,16,16,false);
            NativeImage[] first = maps(ext);
            for (int i=0;i<first.length;++i) {
                require(first[i]!=null,"missing default map");
                for (int pixel=0;pixel<256;++pixel)
                    require(org.lwjgl.system.MemoryUtil.memGetInt(pointer(first[i])+pixel*4L)==(i==1?0xff000000:0),"default material pixel");
            }
            for (int frame=0;frame<12;++frame)
                AuxiliaryTextures.loadAndUpload(source,ext,0,frame%2*16,0,0,0,16,16,false);
            for (int i=0;i<3;++i) require(maps(ext)[i]==first[i],"animated upload rebuilt a cached map");
            AuxiliaryTextures.loadAndUpload(source,ext,1,0,0,0,0,16,16,false);
            NativeImage[] mip = maps(ext);
            for (int i=0;i<3;++i) require(mip[i]!=first[i] && pointer(first[i])==0,"mip invalidation/lifetime");
            ext.radiance$setIdentifier(Identifier.of("radiance","textures/block/material_cache_self_test_changed.png"));
            AuxiliaryTextures.loadAndUpload(source,ext,1,0,0,0,0,16,16,false);
            NativeImage[] changed = maps(ext);
            for (int i=0;i<3;++i) require(changed[i]!=mip[i] && pointer(mip[i])==0,"identifier invalidation");
            ext.radiance$prepareAuxiliaryImages(ext.radiance$getIdentifier(),1,Long.MIN_VALUE);
            for (NativeImage image:changed) require(pointer(image)==0,"generation invalidation");
            AuxiliaryTextures.loadAndUpload(source,ext,1,0,0,0,0,16,16,false);
            NativeImage[] last = maps(ext);
            source.close();
            for (NativeImage image:last) require(pointer(image)==0,"source closure must free cached maps");
        } finally {
            source.close();
            TextureUtil.releaseTextureId(texture);
        }
        System.out.println("[Radiance] Material cache default pixels/12 repeated uploads/mip/identifier/generation invalidation/source closure PASS");
    }
}
