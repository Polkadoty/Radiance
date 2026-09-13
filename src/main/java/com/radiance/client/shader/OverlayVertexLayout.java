package com.radiance.client.shader;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;


/** Explicit byte layouts for mod UI meshes; geometry is uploaded without repacking. */
public final class OverlayVertexLayout {
    // VkFormat values from vulkan_core.h; Java does not load a second Vulkan binding.
    private static final int VK_FORMAT_R16_SINT = 75;
    private static final int VK_FORMAT_R16_SNORM = 71;
    private static final int VK_FORMAT_R16_SSCALED = 73;
    private static final int VK_FORMAT_R16_UINT = 74;
    private static final int VK_FORMAT_R16_UNORM = 70;
    private static final int VK_FORMAT_R16_USCALED = 72;
    private static final int VK_FORMAT_R16G16_SINT = 82;
    private static final int VK_FORMAT_R16G16_SNORM = 78;
    private static final int VK_FORMAT_R16G16_SSCALED = 80;
    private static final int VK_FORMAT_R16G16_UINT = 81;
    private static final int VK_FORMAT_R16G16_UNORM = 77;
    private static final int VK_FORMAT_R16G16_USCALED = 79;
    private static final int VK_FORMAT_R16G16B16_SINT = 89;
    private static final int VK_FORMAT_R16G16B16_SNORM = 85;
    private static final int VK_FORMAT_R16G16B16_SSCALED = 87;
    private static final int VK_FORMAT_R16G16B16_UINT = 88;
    private static final int VK_FORMAT_R16G16B16_UNORM = 84;
    private static final int VK_FORMAT_R16G16B16_USCALED = 86;
    private static final int VK_FORMAT_R16G16B16A16_SINT = 96;
    private static final int VK_FORMAT_R16G16B16A16_SNORM = 92;
    private static final int VK_FORMAT_R16G16B16A16_SSCALED = 94;
    private static final int VK_FORMAT_R16G16B16A16_UINT = 95;
    private static final int VK_FORMAT_R16G16B16A16_UNORM = 91;
    private static final int VK_FORMAT_R16G16B16A16_USCALED = 93;
    private static final int VK_FORMAT_R32_SFLOAT = 100;
    private static final int VK_FORMAT_R32_SINT = 99;
    private static final int VK_FORMAT_R32_UINT = 98;
    private static final int VK_FORMAT_R32G32_SFLOAT = 103;
    private static final int VK_FORMAT_R32G32_SINT = 102;
    private static final int VK_FORMAT_R32G32_UINT = 101;
    private static final int VK_FORMAT_R32G32B32_SFLOAT = 106;
    private static final int VK_FORMAT_R32G32B32_SINT = 105;
    private static final int VK_FORMAT_R32G32B32_UINT = 104;
    private static final int VK_FORMAT_R32G32B32A32_SFLOAT = 109;
    private static final int VK_FORMAT_R32G32B32A32_SINT = 108;
    private static final int VK_FORMAT_R32G32B32A32_UINT = 107;
    private static final int VK_FORMAT_R8_SINT = 14;
    private static final int VK_FORMAT_R8_SNORM = 10;
    private static final int VK_FORMAT_R8_SSCALED = 12;
    private static final int VK_FORMAT_R8_UINT = 13;
    private static final int VK_FORMAT_R8_UNORM = 9;
    private static final int VK_FORMAT_R8_USCALED = 11;
    private static final int VK_FORMAT_R8G8_SINT = 21;
    private static final int VK_FORMAT_R8G8_SNORM = 17;
    private static final int VK_FORMAT_R8G8_SSCALED = 19;
    private static final int VK_FORMAT_R8G8_UINT = 20;
    private static final int VK_FORMAT_R8G8_UNORM = 16;
    private static final int VK_FORMAT_R8G8_USCALED = 18;
    private static final int VK_FORMAT_R8G8B8_SINT = 28;
    private static final int VK_FORMAT_R8G8B8_SNORM = 24;
    private static final int VK_FORMAT_R8G8B8_SSCALED = 26;
    private static final int VK_FORMAT_R8G8B8_UINT = 27;
    private static final int VK_FORMAT_R8G8B8_UNORM = 23;
    private static final int VK_FORMAT_R8G8B8_USCALED = 25;
    private static final int VK_FORMAT_R8G8B8A8_SINT = 42;
    private static final int VK_FORMAT_R8G8B8A8_SNORM = 38;
    private static final int VK_FORMAT_R8G8B8A8_SSCALED = 40;
    private static final int VK_FORMAT_R8G8B8A8_UINT = 41;
    private static final int VK_FORMAT_R8G8B8A8_UNORM = 37;
    private static final int VK_FORMAT_R8G8B8A8_USCALED = 39;
    private OverlayVertexLayout() {}

    public static int[] describe(VertexFormat format) {
        var elements = format.getElements();
        int[] layout = new int[elements.size() * 3];
        for (int i = 0; i < elements.size(); i++) {
            var element = elements.get(i);
            int offset = format.getOffset(element);
            if (offset < 0 || offset + element.getSizeInBytes() > format.getVertexSizeByte())
                throw new IllegalArgumentException("Vertex attribute exceeds stride: " + element);
            layout[3 * i] = i;
            layout[3 * i + 1] = format(element);
            layout[3 * i + 2] = offset;
        }
        return layout;
    }

    private static int format(VertexFormatElement element) {
        int count = element.count();
        if (count < 1 || count > 4) throw new IllegalArgumentException("Unsupported vertex component count: " + count);
        boolean normalized = element.usage() == VertexFormatElement.Usage.COLOR
            || element.usage() == VertexFormatElement.Usage.NORMAL;
        boolean integer = element.usage() == VertexFormatElement.Usage.UV;
        int[] formats = switch (element.type()) {
            case FLOAT -> new int[]{VK_FORMAT_R32_SFLOAT, VK_FORMAT_R32G32_SFLOAT, VK_FORMAT_R32G32B32_SFLOAT, VK_FORMAT_R32G32B32A32_SFLOAT};
            case UBYTE -> normalized
                ? new int[]{VK_FORMAT_R8_UNORM, VK_FORMAT_R8G8_UNORM, VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8A8_UNORM}
                : integer ? new int[]{VK_FORMAT_R8_UINT, VK_FORMAT_R8G8_UINT, VK_FORMAT_R8G8B8_UINT, VK_FORMAT_R8G8B8A8_UINT}
                : new int[]{VK_FORMAT_R8_USCALED, VK_FORMAT_R8G8_USCALED, VK_FORMAT_R8G8B8_USCALED, VK_FORMAT_R8G8B8A8_USCALED};
            case BYTE -> normalized
                ? new int[]{VK_FORMAT_R8_SNORM, VK_FORMAT_R8G8_SNORM, VK_FORMAT_R8G8B8_SNORM, VK_FORMAT_R8G8B8A8_SNORM}
                : integer ? new int[]{VK_FORMAT_R8_SINT, VK_FORMAT_R8G8_SINT, VK_FORMAT_R8G8B8_SINT, VK_FORMAT_R8G8B8A8_SINT}
                : new int[]{VK_FORMAT_R8_SSCALED, VK_FORMAT_R8G8_SSCALED, VK_FORMAT_R8G8B8_SSCALED, VK_FORMAT_R8G8B8A8_SSCALED};
            case USHORT -> normalized
                ? new int[]{VK_FORMAT_R16_UNORM, VK_FORMAT_R16G16_UNORM, VK_FORMAT_R16G16B16_UNORM, VK_FORMAT_R16G16B16A16_UNORM}
                : integer ? new int[]{VK_FORMAT_R16_UINT, VK_FORMAT_R16G16_UINT, VK_FORMAT_R16G16B16_UINT, VK_FORMAT_R16G16B16A16_UINT}
                : new int[]{VK_FORMAT_R16_USCALED, VK_FORMAT_R16G16_USCALED, VK_FORMAT_R16G16B16_USCALED, VK_FORMAT_R16G16B16A16_USCALED};
            case SHORT -> normalized
                ? new int[]{VK_FORMAT_R16_SNORM, VK_FORMAT_R16G16_SNORM, VK_FORMAT_R16G16B16_SNORM, VK_FORMAT_R16G16B16A16_SNORM}
                : integer ? new int[]{VK_FORMAT_R16_SINT, VK_FORMAT_R16G16_SINT, VK_FORMAT_R16G16B16_SINT, VK_FORMAT_R16G16B16A16_SINT}
                : new int[]{VK_FORMAT_R16_SSCALED, VK_FORMAT_R16G16_SSCALED, VK_FORMAT_R16G16B16_SSCALED, VK_FORMAT_R16G16B16A16_SSCALED};
            case INT -> new int[]{VK_FORMAT_R32_SINT, VK_FORMAT_R32G32_SINT, VK_FORMAT_R32G32B32_SINT, VK_FORMAT_R32G32B32A32_SINT};
            case UINT -> new int[]{VK_FORMAT_R32_UINT, VK_FORMAT_R32G32_UINT, VK_FORMAT_R32G32B32_UINT, VK_FORMAT_R32G32B32A32_UINT};
        };
        if ((element.type() == VertexFormatElement.ComponentType.INT || element.type() == VertexFormatElement.ComponentType.UINT)
            && !integer) throw new IllegalArgumentException("32-bit converted/normalized integer attributes require shader conversion");
        return formats[count - 1];
    }
}
