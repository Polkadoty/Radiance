package com.radiance.client.compat;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.joml.Matrix4f;

/** Optional experimental companion bridge; no dependency or change when it is absent. */
public final class DistantHorizonsCompat {
    private static boolean resolved;
    private static Method render;
    private DistantHorizonsCompat() {}
    public static void render(float delta, Matrix4f modelView, Matrix4f projection, int width, int height) {
        if (!resolved) {
            try {
                render = Class.forName("com.radiance.compat.dh.RadianceDhFrameHook")
                        .getMethod("render", float.class, Matrix4f.class, Matrix4f.class, int.class, int.class);
            } catch (ClassNotFoundException absent) {
                // Companion mod is optional.
            } catch (ReflectiveOperationException invalid) {
                throw new IllegalStateException("Incompatible Radiance DH companion bridge", invalid);
            }
            resolved = true;
        }
        if (render == null) return;
        try { render.invoke(null, delta, modelView, projection, width, height); }
        catch (InvocationTargetException failed) {
            throw new IllegalStateException("Radiance DH frame callback failed", failed.getCause());
        } catch (ReflectiveOperationException inaccessible) {
            throw new IllegalStateException("Cannot call Radiance DH companion bridge", inaccessible);
        }
    }
}
