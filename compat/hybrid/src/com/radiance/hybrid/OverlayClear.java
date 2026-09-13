// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Clear transparent overlays regardless of the preceding mod's write masks. */
final class OverlayClear {
    private OverlayClear() {}
    static void clear(RenderTarget target) {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int front = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int back = GL11.glGetInteger(GL20.GL_STENCIL_BACK_WRITEMASK);
        try (var stack = MemoryStack.stackPush()) {
            var color = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, color);
            try {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glColorMask(true, true, true, true);
                GL11.glDepthMask(true);
                GL20.glStencilMaskSeparate(GL11.GL_FRONT, -1);
                GL20.glStencilMaskSeparate(GL11.GL_BACK, -1);
                target.clear(false);
            } finally {
                GL11.glColorMask(color.get(0) != 0, color.get(1) != 0, color.get(2) != 0, color.get(3) != 0);
                GL11.glDepthMask(depth);
                GL20.glStencilMaskSeparate(GL11.GL_FRONT, front);
                GL20.glStencilMaskSeparate(GL11.GL_BACK, back);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }
}
