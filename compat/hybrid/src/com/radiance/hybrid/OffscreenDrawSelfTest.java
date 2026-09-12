// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.shaders.Uniform;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;

/** Pixel evidence for a reordered/padded layout, integer attributes, uniforms and texture mirroring. */
public final class OffscreenDrawSelfTest {
    private OffscreenDrawSelfTest() {}
    public static void run() {
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport=new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int[] caps={GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_BLEND,GL11.GL_SCISSOR_TEST,GL30.GL_FRAMEBUFFER_SRGB};
        boolean[] enabled=new boolean[caps.length];
        for(int i=0;i<caps.length;i++){enabled[i]=GL11.glIsEnabled(caps[i]);GL11.glDisable(caps[i]);}
        int fbo=GL30.glGenFramebuffers(), target=GL11.glGenTextures(), source=0;
        OffscreenRenderer.Program program=null;
        int unitZeroTexture=0;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            unitZeroTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,target);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,32,32,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("GL draw test target incomplete");
            if(!OffscreenRenderer.isPrivateTarget())throw new IllegalStateException("GL draw target routing failed");
            GL11.glViewport(0,0,32,32);
            source=TextureUtil.generateTextureId();
            TextureUtil.prepareImage(source,4,4);
            try(NativeImage pixels=new NativeImage(4,4,false)) {
                pixels.fillRect(0,0,4,4,0xff80ff40);
                ((INativeImageExt)(Object)pixels).radiance$setTargetID(source);
                pixels.upload(0,0,0,false);
            }
            program=OffscreenRenderer.compile("""
                #version 150
                in vec3 Position; in vec4 Color; in ivec2 TestInteger;
                uniform mat4 Transform;
                out vec4 color;
                void main(){gl_Position=Transform*vec4(Position,1);color=Color*float(TestInteger==ivec2(3,7));}
                """, """
                #version 150
                in vec4 color; uniform sampler2D Sampler0; uniform float Gain; uniform int Enabled;
                out vec4 fragColor;
                void main(){fragColor=color*texture(Sampler0,vec2(0.5))*Gain*float(Enabled);}
                """);
            GL20.glUseProgram(program.id());
            try(Uniform matrix=new Uniform("Transform",10,16,null); Uniform gain=new Uniform("Gain",4,1,null); Uniform on=new Uniform("Enabled",0,1,null)) {
                matrix.set(new org.joml.Matrix4f()); gain.set(1f); on.set(1);
                OffscreenRenderer.uploadUniform(program.id(),matrix);
                OffscreenRenderer.uploadUniform(program.id(),gain);
                OffscreenRenderer.uploadUniform(program.id(),on);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,source);
            GL20.glUniform1i(GL20.glGetUniformLocation(program.id(),"Sampler0"),0);
            VertexFormat format=VertexFormat.builder().add("Color",VertexFormatElement.COLOR).padding(4)
                .add("Position",VertexFormatElement.POSITION).add("TestInteger",VertexFormatElement.UV1).build();
            for(int test=0;test<3;test++) {
                var mode=test==0?VertexFormat.Mode.TRIANGLES:VertexFormat.Mode.QUADS;
                float[][] positions=test==0?new float[][]{{-1,-1},{3,-1},{-1,3}}:new float[][]{{-1,-1},{1,-1},{1,1},{-1,1}};
                try(ByteBufferBuilder bytes=new ByteBufferBuilder(256); ByteBufferBuilder sorted=new ByteBufferBuilder(256)) {
                    ByteBuffer data=MemoryUtil.memByteBuffer(bytes.reserve(positions.length*format.getVertexSize()),positions.length*format.getVertexSize()).order(ByteOrder.nativeOrder());
                    for(int i=0;i<positions.length;i++) {
                        int base=i*format.getVertexSize();
                        data.putInt(base+format.getOffset(VertexFormatElement.COLOR),0xffff80ff);
                        int p=base+format.getOffset(VertexFormatElement.POSITION);
                        data.putFloat(p,positions[i][0]);data.putFloat(p+4,positions[i][1]);data.putFloat(p+8,0);
                        p=base+format.getOffset(VertexFormatElement.UV1);data.putShort(p,(short)3);data.putShort(p+2,(short)7);
                    }
                    try(MeshData mesh=new MeshData(bytes.build(),new MeshData.DrawState(format,positions.length,mode.indexCount(positions.length),mode,VertexFormat.IndexType.SHORT))) {
                        if(test==2)mesh.sortQuads(sorted,VertexSorting.DISTANCE_TO_ORIGIN);
                        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});
                        OffscreenRenderer.drawGeometry(mesh,program);
                        ByteBuffer pixel=MemoryUtil.memAlloc(4);
                        try {
                            GL11.glReadPixels(16,16,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                            int[] expected={64,128,128,255};
                            for(int c=0;c<4;c++)if(Math.abs(Byte.toUnsignedInt(pixel.get(c))-expected[c])>1)
                                throw new IllegalStateException("GL draw test "+test+" channel "+c+" expected "+expected[c]+" actual "+Byte.toUnsignedInt(pixel.get(c)));
                        } finally {MemoryUtil.memFree(pixel);}
                    }
                }
            }
            int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("GL draw test error "+error);
            System.out.println("[Hybrid] GL offscreen draw/texture upload/uniform/custom layout/integer attribute/triangle/quad/sorted-index pixel checks PASS");
        } finally {
            GL20.glUseProgram(oldProgram);
            if(program!=null)GL20.glDeleteProgram(program.id());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(target);if(source!=0)TextureUtil.releaseTextureId(source);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,unitZeroTexture);
            GL13.glActiveTexture(oldActive);GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<caps.length;i++)if(enabled[i])GL11.glEnable(caps[i]);else GL11.glDisable(caps[i]);
        }
    }
}
