// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid.mixin;
import com.radiance.hybrid.HybridContext;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real GL-private offscreen targets. Composition into Radiance is a separate stage. */
@Mixin(value = RenderTarget.class, remap = false)
public abstract class RenderTargetMixin {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow public int viewWidth;
    @Shadow public int viewHeight;
    @Shadow public int frameBufferId;
    @Shadow protected int colorTextureId;
    @Shadow protected int depthBufferId;
    @Shadow public int filterMode;
    @Shadow @Final public boolean useDepth;
    @Shadow @Final private float[] clearChannels;
    @Shadow private boolean stencilEnabled;
    @Unique private boolean hybrid$owned;

    @Unique private static int hybrid$texture(int w, int h, int format) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, format, w, h);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    @Inject(method = "createBuffers", at = @At("HEAD"), cancellable = true)
    private void hybrid$create(int w, int h, boolean checkError, CallbackInfo ci) {
        if (!HybridContext.ENABLED) return;
        HybridContext.assertCurrent();
        int maximum = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (w <= 0 || h <= 0 || w > maximum || h > maximum) throw new IllegalArgumentException("Invalid hybrid framebuffer size");
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        width = viewWidth = w; height = viewHeight = h; filterMode = GL11.GL_NEAREST;
        hybrid$owned = true;
        try {
            frameBufferId = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);
            colorTextureId = hybrid$texture(w, h, GL11.GL_RGBA8);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTextureId, 0);
            if (useDepth) {
                depthBufferId = hybrid$texture(w, h, stencilEnabled ? GL30.GL_DEPTH32F_STENCIL8 : GL30.GL_DEPTH_COMPONENT32F);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    stencilEnabled ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthBufferId, 0);
            }
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Hybrid framebuffer incomplete: " + status);
            // Clear attachments explicitly without mutating Vulkan's clear state.
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearChannels);
            hybrid$clearDepthStencil();
        } catch (RuntimeException failure) {
            hybrid$delete();
            throw failure;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
        }
        ci.cancel();
    }

    @Unique private void hybrid$delete() {
        if (colorTextureId > 0) GL11.glDeleteTextures(colorTextureId);
        if (depthBufferId > 0) GL11.glDeleteTextures(depthBufferId);
        if (frameBufferId > 0) GL30.glDeleteFramebuffers(frameBufferId);
        colorTextureId = depthBufferId = frameBufferId = -1;
        hybrid$owned = false;
    }

    @Unique private void hybrid$clearDepthStencil() {
        if (!useDepth) return;
        if (stencilEnabled) GL30.glClearBufferfi(GL30.GL_DEPTH_STENCIL, 0, 1.0f, 0);
        else GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[]{1.0f});
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"), cancellable = true)
    private void hybrid$destroy(CallbackInfo ci) {
        if (!hybrid$owned) return;
        HybridContext.assertCurrent(); hybrid$delete(); ci.cancel();
    }

    @Inject(method = "_bindWrite", at = @At("HEAD"), cancellable = true)
    private void hybrid$bind(boolean viewport, CallbackInfo ci) {
        if (!hybrid$owned) return;
        HybridContext.assertCurrent();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);
        if (viewport) GL11.glViewport(0, 0, viewWidth, viewHeight);
        ci.cancel();
    }

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void hybrid$clear(boolean checkError, CallbackInfo ci) {
        if (!hybrid$owned) return;
        HybridContext.assertCurrent();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frameBufferId);
        try {
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearChannels);
            hybrid$clearDepthStencil();
        } finally { GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); }
        ci.cancel();
    }
}
