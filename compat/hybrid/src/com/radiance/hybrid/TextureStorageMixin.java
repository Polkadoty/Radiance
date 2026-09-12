// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.radiance.hybrid.HybridContext;
import org.lwjgl.opengl.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TextureUtil.class, priority = 1100, remap = false)
public abstract class TextureStorageMixin {
    @Inject(method = "prepareImage(Lcom/mojang/blaze3d/platform/NativeImage$InternalGlFormat;IIII)V", at = @At("HEAD"))
    private static void hybrid$storage(NativeImage.InternalGlFormat format, int id, int maxLevel, int width, int height, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, maxLevel);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            for (int level = 0; level <= maxLevel; level++)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, format.glFormat(), Math.max(1,width>>level), Math.max(1,height>>level),
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, old);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, oldUnpack);
        }
    }
}
