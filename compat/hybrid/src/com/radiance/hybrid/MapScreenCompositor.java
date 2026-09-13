// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Xaero's screen also draws custom vertex layouts directly to the main target. */
public final class MapScreenCompositor {
    private static TextureTarget target;
    private static RenderTarget main;
    private static int texture, oldDraw, oldRead;
    private static final int[] viewport = new int[4];
    private static boolean active, logged;
    private MapScreenCompositor() {}

    public static void begin(Screen screen, GuiGraphics graphics) {
        if (!HybridContext.ENABLED || !screen.getClass().getName().startsWith("xaero.map.gui.")) return;
        if (active) throw new IllegalStateException("Nested map screen composition");
        graphics.flush();
        var mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth(), height = mc.getWindow().getHeight();
        oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (target == null) {
            target = new TextureTarget(width, height, true, false);
            target.enableStencil(); target.setClearColor(0, 0, 0, 0);
            texture = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(texture, 0, width, height);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
            TextureUtil.prepareImage(texture, 0, width, height);
        }
        OverlayClear.clear(target);
        main = ((HybridMainTargetAccess)mc).hybrid$swapMainTarget(target);
        active = true;
        target.bindWrite(true);
    }

    public static void end(GuiGraphics graphics) {
        if (!active) return;
        try {
            graphics.flush();
            NativeInterop.publishHud(target.frameBufferId, target.width, target.height, texture);
        } finally {
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(main);
            main = null; active = false;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        HudCompositor.composite(texture, target.width, target.height);
        if (!logged) {
            System.out.println("[Hybrid] Xaero map screen custom-layout composition active");
            logged = true;
        }
    }

    public static void close() {
        if (active) ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(main);
        active = false; main = null;
        if (target != null) { target.destroyBuffers(); target = null; }
        if (texture != 0) { TextureUtil.releaseTextureId(texture); texture = 0; }
    }
}
