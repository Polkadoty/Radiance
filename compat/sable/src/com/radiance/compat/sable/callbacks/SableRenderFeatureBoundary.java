package com.radiance.compat.sable.callbacks;

import java.util.EnumSet;
import java.util.Set;

/** Startup-only feature partition; simulation and CPU water-occlusion data are independent. */
public final class SableRenderFeatureBoundary {
    public enum Feature {
        SKYLIGHT_SHADOW_MAP, WATER_SURFACE_OCCLUSION, DIRECTIONAL_SHADER,
        VEIL_SHADER_PROCESSORS, VEIL_EDITOR
    }
    private static final boolean ENABLED = Boolean.getBoolean("radiance.sableBridge");
    private static final EnumSet<Feature> REPORTED = EnumSet.noneOf(Feature.class);
    private SableRenderFeatureBoundary() {}
    public static boolean enabled() { return ENABLED; }
    public static void validateBackend() {
        if (ENABLED && !"radiance".equals(System.getProperty("veil.backend"))) {
            throw new IllegalStateException("radiance.sableBridge requires the patched Veil backend selected with -Dveil.backend=radiance");
        }
    }
    public static boolean effectiveEnabled(boolean requested, Feature feature) {
        if (!ENABLED) return requested;
        if (requested) omit(feature);
        return false;
    }
    public static synchronized void omit(Feature feature) {
        if (ENABLED && REPORTED.add(feature)) {
            System.getLogger("Radiance/Sable").log(System.Logger.Level.WARNING,
                    "Sable OpenGL effect {0} is unavailable on the Radiance backend; CPU simulation/data remain active.", feature);
        }
    }
    public static synchronized Set<Feature> omittedFeatures() { return Set.copyOf(REPORTED); }
}
