package com.radiance.client.proxy.world;

import java.nio.ByteBuffer;

/** Experimental render-thread API. Direct packets are copied synchronously; never mutate during a call. */
public final class PersistentSceneProxy {
    private PersistentSceneProxy() {}
    public static native long reset();
    public static native void material(long epoch, long id, long revision, int textureId, int alphaMode, float emission);
    public static native void mesh(long epoch, long id, long revision, long materialId, long materialRevision,
                                  ByteBuffer packet, int byteLength);
    public static native void instances(long epoch, ByteBuffer packet, int byteLength);
    public static native void retireMesh(long epoch, long id);
    public static native void retireMaterial(long epoch, long id);
}
