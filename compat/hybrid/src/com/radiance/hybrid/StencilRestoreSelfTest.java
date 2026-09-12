package com.radiance.hybrid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.PipelineStateProxy.DepthStencilState;
import net.neoforged.neoforge.client.GlStateBackup;
import org.lwjgl.opengl.GL11;

/** Reproduces NeoForge ItemDecoratorHandler's state restore after drawing chest items. */
public final class StencilRestoreSelfTest {
    public static void run() {
        RenderSystem.assertOnRenderThread();
        GlStateBackup original = new GlStateBackup();
        RenderSystem.backupGlState(original);
        try {
            int[] comparisons = {GL11.GL_NEVER, GL11.GL_LESS, GL11.GL_EQUAL, GL11.GL_LEQUAL,
                GL11.GL_GREATER, GL11.GL_NOTEQUAL, GL11.GL_GEQUAL, GL11.GL_ALWAYS};
            for (int i = 0; i < comparisons.length; ++i) {
                int compare = comparisons[i];
                if (VulkanConstants.VkCompareOp.ofGL(compare) != i)
                    throw new IllegalStateException("Incorrect stencil comparison conversion");
                GlStateManager._stencilFunc(compare, 17, 0x3f);
                GlStateManager._stencilOp(GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_REPLACE);
                GlStateManager._stencilMask(0x5a);
                GlStateBackup saved = new GlStateBackup();
                RenderSystem.backupGlState(saved);
                GlStateManager._stencilFunc(comparisons[(i+1)%8], 2, 0xff);
                GlStateManager._stencilOp(GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ZERO);
                GlStateManager._stencilMask(0xff);
                RenderSystem.restoreGlState(saved);
                if (GL11.glGetInteger(GL11.GL_STENCIL_FUNC) != compare
                    || GL11.glGetInteger(GL11.GL_STENCIL_REF) != 17
                    || GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK) != 0x3f
                    || GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) != 0x5a
                    || GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS) != GL11.GL_REPLACE)
                    throw new IllegalStateException("NeoForge stencil restore failed");
                for (int face : new int[]{GL11.GL_FRONT, GL11.GL_BACK, GL11.GL_FRONT_AND_BACK}) {
                    DepthStencilState.glSetStencilFuncSeparate(face, compare, 17, 0x3f);
                    DepthStencilState.glSetStencilOpSeparate(face, GL11.GL_KEEP, GL11.GL_INCR, GL11.GL_REPLACE);
                }
            }
        } finally { RenderSystem.restoreGlState(original); }
        System.out.println("[Hybrid] NeoForge item-decoration GL state restore / all 8 stencil comparisons / front-back dispatch PASS");
    }
}
