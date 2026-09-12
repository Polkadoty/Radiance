// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Exercise the actual transformed Minecraft dispatcher, not only the GL helper. */
public final class OffscreenDispatchSelfTest {
    private OffscreenDispatchSelfTest() {}
    public static void run(RenderTarget target) {
        var previousShader=RenderSystem.getShader();
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int[] caps={GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST};
        boolean[] previous=new boolean[caps.length];
        for(int i=0;i<caps.length;i++){previous[i]=GL11.glIsEnabled(caps[i]);GL11.glDisable(caps[i]);}
        VertexFormat layout=VertexFormat.builder().add("Color",VertexFormatElement.COLOR).padding(4).add("Position",VertexFormatElement.POSITION).build();
        ResourceProvider resources=id->{
            String source=switch(id.getPath()) {
                case "shaders/core/hybrid_dispatch_test.json" -> """
                    {"vertex":"hybrid_dispatch_test","fragment":"hybrid_dispatch_test","samplers":[],"uniforms":[]}
                    """;
                case "shaders/core/hybrid_dispatch_test.vsh" -> """
                    #version 150
                    in vec3 Position; in vec4 Color; out vec4 color;
                    void main(){gl_Position=vec4(Position,1);color=Color;}
                    """;
                case "shaders/core/hybrid_dispatch_test.fsh" -> """
                    #version 150
                    in vec4 color; out vec4 fragColor;
                    void main(){fragColor=color;}
                    """;
                default -> null;
            };
            if(source==null)return Optional.empty();
            return Optional.of(new Resource(null,()->new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))) {
                @Override public String sourcePackId(){return "hybrid-dispatch-self-test";}
            });
        };
        try(ShaderInstance shader=new ShaderInstance(resources,"hybrid_dispatch_test",layout);
            ByteBufferBuilder bytes=new ByteBufferBuilder(256)) {
            RenderSystem.setShader(()->shader);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target.frameBufferId);
            GL11.glViewport(0,0,target.viewWidth,target.viewHeight);
            GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});
            BufferBuilder builder=new BufferBuilder(bytes,VertexFormat.Mode.TRIANGLES,layout);
            builder.addVertex(-1,-1,0).setColor(255,128,64,255);
            builder.addVertex(3,-1,0).setColor(255,128,64,255);
            builder.addVertex(-1,3,0).setColor(255,128,64,255);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            int realProgram=shader.getId();
            if(!GL20.glIsProgram(realProgram))throw new IllegalStateException("ShaderInstance returned a non-GL program ID");
            // Reproduce mods that bind getId() after drawWithShader and batch bare draws.
            com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(realProgram);
            BufferBuilder next=new BufferBuilder(bytes,VertexFormat.Mode.TRIANGLES,layout);
            next.addVertex(-1,-1,0).setColor(16,64,255,255);
            next.addVertex(3,-1,0).setColor(16,64,255,255);
            next.addVertex(-1,3,0).setColor(16,64,255,255);
            BufferUploader.draw(next.buildOrThrow());
            com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(0);
            ByteBuffer pixel=MemoryUtil.memAlloc(4);
            try {
                GL11.glReadPixels(target.viewWidth/2,target.viewHeight/2,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                int[] expected={16,64,255,255};
                for(int c=0;c<4;c++)if(Math.abs(Byte.toUnsignedInt(pixel.get(c))-expected[c])>1)
                    throw new IllegalStateException("Minecraft draw dispatcher pixel mismatch at channel "+c+": "+Byte.toUnsignedInt(pixel.get(c)));
            } finally {MemoryUtil.memFree(pixel);}
            int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("Minecraft dispatch GL error "+error);
            System.out.println("[Hybrid] Minecraft BufferUploader dispatch with custom ShaderInstance/layout and overridden main target pixel check PASS");
            System.out.println("[Hybrid] ShaderInstance GL program identity and bound-program BufferUploader.draw pixel check PASS");
        } catch(IOException failure){throw new IllegalStateException("Cannot load dispatch test shader",failure);}
        finally {
            RenderSystem.setShader(()->previousShader);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<caps.length;i++)if(previous[i])GL11.glEnable(caps[i]);else GL11.glDisable(caps[i]);
        }
    }
}
