// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import com.radiance.hybrid.HybridContext;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import org.lwjgl.opengl.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NativeImage.class, priority = 1100, remap = false)
public abstract class TextureUploadMixin {
    @Shadow private long pixels;
    @Shadow @Final private int width;
    @Shadow @Final private NativeImage.Format format;
    @Inject(method = "_upload", at = @At("HEAD"))
    private void hybrid$upload(int level, int x, int y, int skipX, int skipY, int w, int h,
                              boolean blur, boolean clamp, boolean mipmap, boolean close, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        int id = ((INativeImageExt)(Object)this).radiance$getTargetID();
        if (id < 0 || pixels == 0) return; // Let Radiance report its existing allocation/target error.
        int[] keys = {GL11.GL_UNPACK_ALIGNMENT,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_UNPACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_ROWS};
        int[] old = new int[keys.length];
        for (int i=0;i<keys.length;i++) old[i]=GL11.glGetInteger(keys[i]);
        int oldUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,width);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,skipX);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,skipY);
            GL45.glTextureParameteri(id,GL11.GL_TEXTURE_MIN_FILTER,blur ? (mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR) : (mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST));
            GL45.glTextureParameteri(id,GL11.GL_TEXTURE_MAG_FILTER,blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            GL45.glTextureParameteri(id,GL11.GL_TEXTURE_WRAP_S,clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
            GL45.glTextureParameteri(id,GL11.GL_TEXTURE_WRAP_T,clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
            GL45.glTextureSubImage2D(id,level,x,y,w,h,format.glFormat(),GL11.GL_UNSIGNED_BYTE,pixels);
        } finally {
            for (int i=0;i<keys.length;i++) GL11.glPixelStorei(keys[i],old[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,oldUnpack);
        }
    }
}
