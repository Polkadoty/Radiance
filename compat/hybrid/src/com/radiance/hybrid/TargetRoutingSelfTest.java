// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import java.util.function.Consumer;

/** Reproduce consumers that temporarily replace Minecraft.mainRenderTarget. */
public final class TargetRoutingSelfTest {
    private TargetRoutingSelfTest() {}
    public static void run(RenderTarget owned, Consumer<RenderTarget> replaceMain) {
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int width=owned.viewWidth, height=owned.viewHeight;
        TextureTarget first=new TextureTarget(32,32,false,false), second=null;
        try {
            second=new TextureTarget(16,16,false,false);
            check(owned,false);
            check(first,true);
            replaceMain.accept(first);
            if(Minecraft.getInstance().getMainRenderTarget()!=first)throw new IllegalStateException("Main-target test replacement did not apply");
            check(first,true); check(owned,false);
            OffscreenDispatchSelfTest.run(first);
            replaceMain.accept(second);
            check(second,true); check(first,true); check(owned,false);
            // The registered object stays stable across ID changes, even while a
            // consumer has overridden the public main target with its own FBO.
            HybridTargets.resize(64,48);
            check(owned,false); check(second,true);
            replaceMain.accept(first);check(first,true);
            replaceMain.accept(owned);check(first,true);check(owned,false);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);
            if(OffscreenRenderer.isPrivateTarget())throw new IllegalStateException("Default FBO misclassified");
            System.out.println("[Hybrid] Main-target override/nested override/owned resize/restoration/default-FBO routing checks PASS");
        } finally {
            replaceMain.accept(owned);
            HybridTargets.resize(width,height);
            first.destroyBuffers();if(second!=null)second.destroyBuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
        }
    }
    private static void check(RenderTarget target, boolean expected) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);
        if(OffscreenRenderer.isPrivateTarget()!=expected)
            throw new IllegalStateException("Hybrid target ownership routing failed for FBO "+target.frameBufferId);
    }
}
