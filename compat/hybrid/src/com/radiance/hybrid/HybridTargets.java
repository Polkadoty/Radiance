// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;
import com.mojang.blaze3d.pipeline.RenderTarget;

/** Backend ownership is independent of mods' temporary Minecraft main-target overrides. */
public final class HybridTargets {
    private static RenderTarget presentationTarget;
    private HybridTargets() {}
    public static void registerPresentationTarget(RenderTarget target) {
        HybridContext.assertCurrent();
        if (presentationTarget != null && presentationTarget != target)
            throw new IllegalStateException("Hybrid presentation target already registered");
        presentationTarget = target;
    }
    public static boolean isPresentationTarget(int framebuffer) {
        // Read the owned object's current ID: resize destroys and recreates the FBO.
        return presentationTarget != null && framebuffer == presentationTarget.frameBufferId;
    }
    public static void resize(int width, int height) {
        if (presentationTarget != null) presentationTarget.resize(width, height, false);
    }
    public static void destroy() {
        if (presentationTarget != null) {
            presentationTarget.destroyBuffers();
            presentationTarget = null;
        }
    }
}
