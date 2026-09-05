package com.radiance.client.compat;

import java.nio.ByteBuffer;
import net.minecraft.client.render.VertexFormat;

/** Vanilla sequential indices and topology for immediate Vulkan overlay draws. */
public final class OverlayIndices {
    private OverlayIndices() {}

    public static VertexFormat.DrawMode topology(VertexFormat.DrawMode mode) {
        // Vanilla's thick-line shader expands duplicated vertices into triangles.
        return switch (mode) {
            case LINES, QUADS -> VertexFormat.DrawMode.TRIANGLES;
            case LINE_STRIP -> VertexFormat.DrawMode.TRIANGLE_STRIP;
            default -> mode;
        };
    }

    public static void write(ByteBuffer target, VertexFormat.DrawMode mode,
        VertexFormat.IndexType type, int vertexCount, int indexCount) {
        if (vertexCount < 0 || indexCount != mode.getIndexCount(vertexCount)
            || target.remaining() != Math.multiplyExact(indexCount, type.size)) {
            throw new IllegalArgumentException("Invalid overlay index-buffer dimensions");
        }
        if (type == VertexFormat.IndexType.SHORT && vertexCount > 65536) {
            throw new IllegalArgumentException("Overlay vertices exceed unsigned-short indices");
        }
        if (mode == VertexFormat.DrawMode.LINES || mode == VertexFormat.DrawMode.QUADS) {
            if (vertexCount % 4 != 0) {
                throw new IllegalArgumentException("Expanded lines and quads need groups of four vertices");
            }
            for (int i = 0; i < vertexCount; i += 4) {
                put(target, type, i);
                put(target, type, i + 1);
                put(target, type, i + 2);
                put(target, type, mode == VertexFormat.DrawMode.LINES ? i + 3 : i + 2);
                put(target, type, mode == VertexFormat.DrawMode.LINES ? i + 2 : i + 3);
                put(target, type, mode == VertexFormat.DrawMode.LINES ? i + 1 : i);
            }
        } else {
            for (int i = 0; i < vertexCount; i++) {
                put(target, type, i);
            }
        }
        target.flip();
    }

    private static void put(ByteBuffer target, VertexFormat.IndexType type, int index) {
        if (type == VertexFormat.IndexType.SHORT) {
            target.putShort((short) index);
        } else {
            target.putInt(index);
        }
    }
}
