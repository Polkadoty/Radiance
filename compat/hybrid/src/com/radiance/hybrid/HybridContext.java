// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** Render-thread GL context; the visible game window remains Vulkan-owned. */
public final class HybridContext {
    public static final boolean ENABLED = Boolean.getBoolean("radiance.hybrid");
    private static long hiddenWindow;
    private static Thread owner;
    private static volatile org.lwjgl.opengl.GLCapabilities capabilitySnapshot;
    private static org.lwjgl.opengl.GLDebugMessageCallback debugCallback;

    private HybridContext() {}

    public static void open() {
        if (!ENABLED || hiddenWindow != 0) return;
        owner = Thread.currentThread();
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        try {
            hiddenWindow = GLFW.glfwCreateWindow(64, 64, "Radiance hybrid context", 0, 0);
            if (hiddenWindow == 0) throw new IllegalStateException("Cannot create hybrid OpenGL context");
            GLFW.glfwMakeContextCurrent(hiddenWindow);
            var caps = GL.createCapabilities();
            capabilitySnapshot = caps;
            if (!caps.GL_EXT_memory_object_win32 || !caps.GL_EXT_semaphore_win32)
                throw new IllegalStateException("Hybrid backend requires Windows external memory and semaphore imports");
            if(Boolean.getBoolean("radiance.hybridSelfTest")) {
                debugCallback=org.lwjgl.opengl.GLDebugMessageCallback.create((source,type,id,severity,length,message,user)->{
                    if(type!=org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR)return;
                    System.err.println("[Hybrid] GL driver error: "+org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length,message));
                    StackTraceElement[] trace=Thread.currentThread().getStackTrace();
                    for(int i=2;i<Math.min(trace.length,14);i++)System.err.println("[Hybrid] GL caller: "+trace[i]);
                });
                GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT);
                GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                org.lwjgl.opengl.GL43.glDebugMessageCallback(debugCallback,0);
            }
            System.out.println("[Hybrid] Real OpenGL context ready: " + GL11.glGetString(GL11.GL_RENDERER)
                + "; " + GL11.glGetString(GL11.GL_VERSION));
        } catch (RuntimeException failure) {
            close();
            throw failure;
        } finally {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        }
    }

    public static void assertCurrent() {
        if (owner != Thread.currentThread() || hiddenWindow == 0 || GLFW.glfwGetCurrentContext() != hiddenWindow)
            throw new IllegalStateException("Hybrid GL operation outside its owning render context");
    }

    /** Immutable feature flags only; callers must never invoke the stored function pointers. */
    public static org.lwjgl.opengl.GLCapabilities capabilitiesSnapshot() {
        var snapshot = capabilitySnapshot;
        if (snapshot == null) throw new IllegalStateException("Hybrid capabilities queried before context initialization");
        return snapshot;
    }

    public static void close() {
        if (hiddenWindow == 0) return;
        assertCurrent();
        OffscreenRenderer.close();
        if(debugCallback!=null){org.lwjgl.opengl.GL43.glDebugMessageCallback(null,0);debugCallback.free();debugCallback=null;}
        GL.setCapabilities(null);
        GLFW.glfwMakeContextCurrent(0);
        GLFW.glfwDestroyWindow(hiddenWindow);
        hiddenWindow = 0;
        capabilitySnapshot = null;
        owner = null;
        System.out.println("[Hybrid] GL context closed");
    }
}
