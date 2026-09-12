// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

public final class NativeInterop {
    private NativeInterop() {}
    /** Startup-only test using the initialized Radiance device, not a second Vulkan device. */
    public static native boolean verifyRendererSharing();
    public static native void enableTextureNamespace();
    public static native void publishHud(int framebuffer,int width,int height,int destinationTexture);
    /** Startup diagnostic only; production publishes remain entirely on the GPU. */
    public static native int readHudPixel(int texture,int x,int y);
    public static native void closeHud();
}
