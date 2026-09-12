package com.radiance.compat.dh;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Owns only DH-produced quads. Never creates a water plane from altitude/color. */
public final class LodWaterParts {
    private LodWaterParts() {}
    public record Part(CapturedLodBuffer.Snapshot snapshot, int flags) {}

    public static List<Part> capture(LodBufferContainer container) {
        List<Part> parts = new ArrayList<>();
        append(parts, container.vboOpaqueWrappers, true);
        append(parts, container.vboTransparentWrappers, false);
        return LodWaterSurface.mark(parts);
    }

    private static void append(List<Part> parts, IVertexBufferWrapper[] wrappers, boolean opaqueSource) {
        if (wrappers == null) return;
        for (IVertexBufferWrapper wrapper : wrappers) {
            if (wrapper == null) continue;
            if (!(wrapper instanceof CapturedLodBuffer captured))
                throw new IllegalStateException("DH OpenGL VBO escaped Radiance backend selection");
            parts.addAll(split(captured.snapshot(), opaqueSource));
        }
    }

    public static List<Part> split(CapturedLodBuffer.Snapshot source, boolean opaqueSource) {
        ByteBuffer data = source.vertices().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int begin = data.position(), bytes = data.remaining();
        if (bytes % 64 != 0 || bytes > CapturedLodBuffer.MAX_BYTES)
            throw new IllegalArgumentException("Expected complete bounded DH quads");
        int waterBytes = 0, opaqueBytes = 0;
        for (int q = begin; q < begin + bytes; q += 64) {
            int kind = quadKind(data, q, opaqueSource);
            if (kind == LodPacket.WATER) waterBytes += 64;
            else if (kind == LodPacket.OPAQUE) opaqueBytes += 64;
        }
        List<Part> result = new ArrayList<>(2);
        addFiltered(result, source, data, begin, bytes, opaqueBytes, LodPacket.OPAQUE, opaqueSource);
        addFiltered(result, source, data, begin, bytes, waterBytes, LodPacket.WATER, opaqueSource);
        return List.copyOf(result);
    }

    private static int quadKind(ByteBuffer data, int q, boolean opaqueSource) {
        int material = Byte.toUnsignedInt(data.get(q + 12));
        int normal = Byte.toUnsignedInt(data.get(q + 13));
        boolean nonzero = true, opaque = true;
        for (int v = 0; v < 4; ++v) {
            int p = q + v * 16;
            if (Byte.toUnsignedInt(data.get(p + 12)) != material
                    || Byte.toUnsignedInt(data.get(p + 13)) != normal || normal >= 6)
                throw new IllegalArgumentException("Mixed DH material/normal within one quad");
            int alpha = Byte.toUnsignedInt(data.get(p + 11));
            nonzero &= alpha > 0;
            opaque &= alpha == 255;
        }
        if (material == Byte.toUnsignedInt(EDhApiBlockMaterial.WATER.index))
            return nonzero ? LodPacket.WATER : -1;
        // Other translucent materials are outside this water-only increment.
        if (!opaqueSource) return -1;
        if (!opaque) throw new IllegalArgumentException("Non-water translucent vertex in DH opaque VBO");
        return LodPacket.OPAQUE;
    }

    private static void addFiltered(List<Part> result, CapturedLodBuffer.Snapshot source,
            ByteBuffer data, int begin, int bytes, int selected, int wanted, boolean opaqueSource) {
        if (selected == 0) return;
        if (selected == bytes) { result.add(new Part(source, wanted)); return; }
        ByteBuffer copy = ByteBuffer.allocateDirect(selected).order(ByteOrder.LITTLE_ENDIAN);
        for (int q = begin; q < begin + bytes; q += 64) {
            if (quadKind(data, q, opaqueSource) != wanted) continue;
            ByteBuffer quad = data.duplicate();quad.position(q).limit(q + 64);copy.put(quad);
        }
        copy.flip();
        result.add(new Part(new CapturedLodBuffer.Snapshot(source.revision(),
                copy.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)), wanted));
    }
}
