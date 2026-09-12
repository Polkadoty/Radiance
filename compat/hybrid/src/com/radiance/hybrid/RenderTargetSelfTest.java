// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class RenderTargetSelfTest {
    private RenderTargetSelfTest() {}
    public static void run() {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        for (int kind = 0; kind < 2; kind++) {
        RenderTarget target = kind == 0 ? new TextureTarget(160, 90, true, false) : new MainTarget(160,90);
        try {
            for (boolean stencil : new boolean[]{false, true}) {
            if (stencil) target.enableStencil();
            for (int[] size : new int[][]{{160,90},{320,180},{64,32}}) {
                target.resize(size[0], size[1], false);
                target.setClearColor(0, 1, 0, 1); target.clear(false);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
                float[] color = new float[4], depth = new float[1];
                GL11.glReadPixels(1, 1, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, color);
                GL11.glReadPixels(1, 1, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
                if (color[0] != 0 || color[1] != 1 || color[2] != 0 || color[3] != 1 || depth[0] != 1)
                    throw new IllegalStateException("Minecraft RenderTarget clear/resize verification failed");
                if (stencil) {
                    if (!target.isStencilEnabled()) throw new IllegalStateException("Stencil lost during resize");
                    int[] value = new int[1];
                    GL11.glReadPixels(1, 1, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_INT, value);
                    if (value[0] != 0) throw new IllegalStateException("Stencil was not cleared");
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
                    GL30.glClearBufferiv(GL11.GL_STENCIL, 0, new int[]{37});
                    GL11.glReadPixels(1, 1, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_INT, value);
                    if (value[0] != 37) throw new IllegalStateException("Stencil attachment is not writable");
                    target.clear(false);
                    GL11.glReadPixels(1, 1, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_INT, value);
                    if (value[0] != 0) throw new IllegalStateException("Stencil clear did not reset existing data");
                }
                if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("Minecraft RenderTarget GL error");
            }
            }
        } finally {
            target.destroyBuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
        }
        System.out.println("[Hybrid] Minecraft TextureTarget and MainTarget allocation/color/depth/stencil/resize/destruction PASS");
    }
}
