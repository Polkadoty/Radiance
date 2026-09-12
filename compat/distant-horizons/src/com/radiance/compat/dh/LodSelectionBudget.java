package com.radiance.compat.dh;

/** A complete selected set gets half the fixed native live-geometry cap; replacement needs the other half. */
public final class LodSelectionBudget {
    public static final long LIMIT = 16L * 1024 * 1024;
    private long used;
    private final long limit;
    public LodSelectionBudget() { this(LIMIT); }
    public LodSelectionBudget(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative LOD budget");
        this.limit = limit;
    }
    public boolean include(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Negative LOD geometry size");
        if (bytes > Long.MAX_VALUE - used || (limit != 0 && bytes > limit - used)) return false;
        used += bytes;
        return true;
    }
    public long used() { return used; }
}
