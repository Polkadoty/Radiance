package com.radiance.compat.sable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** RPI1 whole-visible-set packet. Mesh and material uploads must already be accepted. */
public final class SableInstancePacket {
    public static final int MAX_INSTANCES = 65536;
    public record Instance(long instanceId, long meshId, long meshRevision, SablePoseSnapshot pose) {
        public Instance {
            if (instanceId <= 0 || meshId <= 0 || meshRevision <= 0) {
                throw new IllegalArgumentException("Persistent IDs and revisions must be positive");
            }
            Objects.requireNonNull(pose, "pose");
        }
    }
    private SableInstancePacket() {}

    public static ByteBuffer encode(List<Instance> visibleInstances) {
        if (visibleInstances.size() > MAX_INSTANCES) throw new IllegalArgumentException("Instance capacity exceeded");
        List<Instance> snapshot = List.copyOf(visibleInstances);
        if (snapshot.size() > MAX_INSTANCES) throw new IllegalArgumentException("Instance capacity exceeded");
        HashSet<Long> ids = new HashSet<>();
        for (Instance instance : snapshot) {
            if (!ids.add(instance.instanceId())) throw new IllegalArgumentException("Duplicate visible instance ID");
        }
        ByteBuffer packet = ByteBuffer.allocateDirect(16 + 84 * snapshot.size()).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(0x31495052).putInt(1).putInt(snapshot.size()).putInt(0);
        for (Instance instance : snapshot) {
            packet.putLong(instance.instanceId()).putLong(instance.meshId()).putLong(instance.meshRevision());
            instance.pose().writeNativeAffine(packet);
        }
        return packet.flip();
    }
}
