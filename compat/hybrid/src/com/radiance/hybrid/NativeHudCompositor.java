// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.constant.Constants;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IShaderProgramExt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Native standard HUD draws, with ordered snapshots only for unsupported main-target draws. */
public final class NativeHudCompositor {
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("radiance.nativeHud", "true"));
    private static final Set<String> SIMPLE_SHADERS = Set.of("position", "position_color", "position_tex",
        "position_tex_color", "position_color_tex", "position_color_lightmap", "position_color_tex_lightmap",
        "position_tex_lightmap_color", "rendertype_gui", "rendertype_gui_overlay");
    // Xaero's pos_tex_alpha_test_pre has a standard layout, but samples the
    // GL-produced minimap attachment. Shader/layout support alone is insufficient:
    // keep that draw in GL until native offscreen target synchronization exists.
    private static final Set<String> reported = new HashSet<>();
    private static final java.util.Map<com.mojang.blaze3d.vertex.VertexFormat, Boolean> customFormats = new java.util.HashMap<>();
    private static final Set<com.mojang.blaze3d.vertex.VertexFormat> FORMATS = java.util.Arrays.stream(Constants.VertexFormats.values())
        .map(Constants.VertexFormats::getVertexFormat).collect(java.util.stream.Collectors.toUnmodifiableSet());
    // Preserve the previous implementation's maximum of two full-resolution snapshots.
    // If a mod interleaves more often, finish this frame's HUD in GL rather than grow the pool.
    private static final int MAX_SNAPSHOTS = 2;
    private static final ArrayList<Integer> snapshots = new ArrayList<>();
    private static TextureTarget target;
    private static RenderTarget main;
    private static boolean active, compatibility, publishing;
    private static int width, height, snapshotIndex, oldDraw, oldRead;
    private static final int[] viewport = new int[4];
    private static long nativeDraws, fallbackDraws, copies, frames, reportAt;
    private NativeHudCompositor() {}

    public static void begin(GuiGraphics graphics) {
        if (!ENABLED || !HybridContext.ENABLED) return;
        if (active) throw new IllegalStateException("Nested native HUD frame");
        graphics.flush();
        var mc = Minecraft.getInstance();
        main = mc.getMainRenderTarget();
        width = mc.getWindow().getWidth(); height = mc.getWindow().getHeight();
        oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        snapshotIndex = 0; compatibility = false; active = true;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** Called before BufferUploader dispatch; private mod FBOs retain their existing route. */
    public static void beforeDraw(MeshData mesh, boolean boundProgram) {
        if (!active || publishing) return;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean ours = compatibility && target != null && draw == target.frameBufferId;
        if (draw != 0 && !ours && !HybridTargets.isPresentationTarget(draw)) return;
        if (!boundProgram && supported(mesh) && !(compatibility && snapshotIndex >= MAX_SNAPSHOTS - 1)) {
            flushCompatibility();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            nativeDraws++;
        } else {
            beginCompatibility();
            fallbackDraws++;
        }
    }

    static boolean supported(MeshData mesh) {
        var shader = RenderSystem.getShader();
        if (shader == null) return false;
        var metadata = (IShaderProgramExt)(Object)shader;
        String name = metadata.radiance$getShaderName();
        if (name == null) return false;
        String local = name.startsWith("minecraft:") ? name.substring(10) : "";
        boolean customColor = local.equals("xaerolib/position_color") || local.equals("xaerolib/position_color_no_alpha_test");
        boolean shaderKnown = SIMPLE_SHADERS.contains(local) || local.startsWith("rendertype_") || customColor;
        boolean formatKnown = FORMATS.contains(mesh.drawState().format())
            && FORMATS.contains(metadata.radiance$getVertexFormat());
        if (customColor && mesh.drawState().format().equals(metadata.radiance$getVertexFormat())) {
            formatKnown = customFormats.computeIfAbsent(metadata.radiance$getVertexFormat(), format -> {
                try { com.radiance.client.shader.OverlayVertexLayout.describe(format); return true; }
                catch (IllegalArgumentException unsupported) { return false; }
            });
        }
        boolean result = shaderKnown && formatKnown;
        if (Boolean.getBoolean("radiance.hybridSelfTest") && reported.add(name))
            System.out.println("[Hybrid] Native HUD route " + name + " -> " + (result ? "Vulkan" : "OpenGL fallback")
                + " (shader=" + shaderKnown + ", layout=" + formatKnown + ")");
        return result;
    }

    private static void beginCompatibility() {
        if (compatibility) { target.bindWrite(false); return; }
        if (target == null) {
            target = new TextureTarget(width, height, true, false);
            target.enableStencil(); target.setClearColor(0, 0, 0, 0);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
            for (int texture : snapshots) TextureUtil.prepareImage(texture, 0, width, height);
        }
        // A new segment must not inherit content or clipping from the previous one.
        OverlayClear.clear(target);
        ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(target);
        target.bindWrite(false);
        compatibility = true;
    }

    private static void flushCompatibility() {
        if (!compatibility) return;
        publishing = true;
        try {
            if (snapshotIndex == snapshots.size()) {
                int texture = TextureUtil.generateTextureId();
                TextureUtil.prepareImage(texture, 0, width, height);
                snapshots.add(texture);
            }
            int texture = snapshots.get(snapshotIndex++);
            NativeInterop.publishHud(target.frameBufferId, width, height, texture);
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(main);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            HudCompositor.composite(texture, width, height);
            copies++;
        } finally { publishing = false; compatibility = false; }
    }

    public static void end(GuiGraphics graphics) {
        if (!active) return;
        try { graphics.flush(); flushCompatibility(); }
        finally {
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(main);
            main = null; active = false;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        frames++;
        long now = System.nanoTime();
        if (now - reportAt > 30_000_000_000L) {
            System.out.println("[Hybrid] Native HUD totals: frames=" + frames + ", Vulkan draws=" + nativeDraws
                + ", fallback draws=" + fallbackDraws + ", segment copies=" + copies + ", snapshot pool=" + snapshots.size());
            reportAt = now;
        }
    }

    public static void close() {
        if (active) ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(main);
        active = false; main = null; compatibility = false;
        if (target != null) { target.destroyBuffers(); target = null; }
        for (int texture : snapshots) TextureUtil.releaseTextureId(texture);
        snapshots.clear();
    }
}
