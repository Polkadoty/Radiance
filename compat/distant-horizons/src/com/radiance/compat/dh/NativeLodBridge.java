package com.radiance.compat.dh;
import java.nio.ByteBuffer;
public final class NativeLodBridge {
    private NativeLodBridge() {}
    public static native void configure(long liveBytes, long queueBytes);
    public static native boolean selectionPublished(long epoch);
    public static native void beginWorld(long epoch);
    public static native boolean upload(ByteBuffer packet);
    public static native void select(long epoch, long[] ids, long[] revisions);
    public static native void closeWorld();
}
