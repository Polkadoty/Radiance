package com.radiance.client.shader;

import com.radiance.client.proxy.vulkan.ShaderProxy;
import java.nio.file.Files;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;

/** Startup registration probe on the real Vulkan device; does not alter the visible HUD. */
public final class OverlayVertexLayoutSelfTest {
    public static void run() {
        var format = VertexFormat.builder().add("Color", VertexFormatElement.COLOR).skip(4)
            .add("Position", VertexFormatElement.POSITION).build();
        int[] description = OverlayVertexLayout.describe(format);
        if (format.getVertexSizeByte() != 20 || !java.util.Arrays.equals(description,
            new int[]{0, 37, 0, 1, 106, 8}))
            throw new IllegalStateException("Custom color/padding/position byte layout changed");
        try {
            var dir = Files.createTempDirectory("radiance-overlay-layout-test");
            var vertex = dir.resolve("test.vert");
            var fragment = dir.resolve("test.frag");
            // Pipeline recreation re-registers these shaders on resource reload/resize.
            // Keep their sources until JVM shutdown, just like ordinary cached UI shaders.
            dir.toFile().deleteOnExit();
            vertex.toFile().deleteOnExit();
            fragment.toFile().deleteOnExit();
            Files.writeString(vertex, """
                #version 460
                layout(location=0) in vec4 Color;
                layout(location=1) in vec3 Position;
                layout(location=0) out vec4 color;
                void main() { gl_Position=vec4(Position,1); color=Color; }
                """);
            Files.writeString(fragment, """
                #version 460
                layout(location=0) in vec4 color;
                layout(location=0) out vec4 outputColor;
                void main() { outputColor=color; }
                """);
            int id = ShaderProxy.registerShaderWithLayout("radiance-custom-layout-self-test", 20, description,
                4, 16, vertex.toString(), fragment.toString());
            if (id < 0) throw new IllegalStateException("Custom native layout was not registered");
            int again = ShaderProxy.registerShaderWithLayout("radiance-custom-layout-self-test", 20, description,
                4, 16, vertex.toString(), fragment.toString());
            if (again != id) throw new IllegalStateException("Custom native layout cache mismatch");
            int[][] invalid = { {0,37,0,0,106,8}, {0,37,19}, {0,-1,0}, {0,37} };
            for (int[] bad : invalid) {
                boolean rejected = false;
                try { ShaderProxy.registerShaderWithLayout("radiance-invalid-layout", 20, bad, 4, 16,
                    vertex.toString(), fragment.toString()); }
                catch (IllegalArgumentException expected) { rejected = true; }
                if (!rejected) throw new IllegalStateException("Invalid native layout accepted");
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Custom layout test shaders", e); }
        System.out.println("[Radiance] Custom padded vertex layout/Vulkan pipeline registration/cache/invalid descriptor rejection PASS");
    }
}
