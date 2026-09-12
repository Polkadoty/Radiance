package com.radiance.compat.dh;

/** Physical output dimensions supplied by Radiance; never touches Minecraft's absent GL target. */
public final class RadianceDhViewport {
    private record Size(int width, int height) {}
    private static volatile Size current;
    private RadianceDhViewport() {}

    public static boolean beginFrame(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative Radiance viewport dimensions");
        if (width == 0 || height == 0) {
            current = null; // Minimized windows have no drawable viewport; skip DH's frame pump.
            return false;
        }
        current = new Size(width, height);
        return true;
    }
    private static Size requireSize() {
        Size size = current;
        if (size == null) throw new IllegalStateException("Radiance DH viewport was not published for a drawable frame");
        return size;
    }
    public static int width() { return requireSize().width(); }
    public static int height() { return requireSize().height(); }
}
