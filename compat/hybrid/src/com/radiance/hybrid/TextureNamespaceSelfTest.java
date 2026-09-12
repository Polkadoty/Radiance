// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import java.nio.IntBuffer;
import java.util.HashSet;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Exercise custom-FBO APIs without using our RenderTarget allocation override. */
public final class TextureNamespaceSelfTest {
    private TextureNamespaceSelfTest() {}
    public static void run() {
        int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._activeTexture(GL13.GL_TEXTURE3);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var allocated = new HashSet<Integer>();
        try {
            for (int iteration = 0; iteration < 16; iteration++) {
                int texture = TextureUtil.generateTextureId();
                int fbo = GL30.glGenFramebuffers();
                try {
                    if (texture <= 0 || !allocated.add(texture))
                        throw new IllegalStateException("Texture identity reused while still reserved by Vulkan");
                    GlStateManager._bindTexture(texture);
                    GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                        32, 24, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer)null);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, texture, 0);
                    if (!GL11.glIsTexture(texture) || GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("TextureUtil identity is not a usable OpenGL framebuffer attachment");
                    GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[]{1,0,1,1});
                    float[] pixel = new float[4];
                    GL11.glReadPixels(1, 1, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                    if (pixel[0] != 1 || pixel[1] != 0 || pixel[2] != 1 || pixel[3] != 1)
                        throw new IllegalStateException("Custom framebuffer pixel readback failed");
                    if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE3 || GL11.glGetError() != GL11.GL_NO_ERROR)
                        throw new IllegalStateException("Custom framebuffer texture-unit state failed");
                } finally {
                    GL30.glDeleteFramebuffers(fbo);
                    TextureUtil.releaseTextureId(texture);
                }
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._bindTexture(previous);
            GlStateManager._activeTexture(unit);
        }
        System.out.println("[Hybrid] Custom FBO TextureUtil allocation/binding/readback/deletion/non-reuse PASS (16 cycles)");
    }
}
