// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import org.lwjgl.opengl.*;

/** Read the registered Vulkan destination, not merely the GL source attachment. */
public final class HudTransferSelfTest {
    private HudTransferSelfTest(){}
    public static void run() {
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] box=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
        int texture=TextureUtil.generateTextureId();
        int secondTexture=TextureUtil.generateTextureId();
        try {
            for(int[] size:new int[][]{{64,32},{93,47},{32,64}}) {
                int w=size[0],h=size[1];var target=new TextureTarget(w,h,false,false);
                try {
                    TextureUtil.prepareImage(texture,0,w,h);
                    TextureUtil.prepareImage(secondTexture,0,w,h);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target.frameBufferId);
                    for(int frame=0;frame<8;frame++) {
                        GL11.glDisable(GL11.GL_SCISSOR_TEST);
                        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});
                        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,w/2,h/2);
                        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{frame%2==0?0.5f:0.25f,0,0,0.5f});
                        GL11.glScissor(w/2,h/2,w-w/2,h-h/2);
                        GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0.25f,1,1});
                        // Keep scissor enabled deliberately: transport must disable it and restore.
                        NativeInterop.publishHud(target.frameBufferId,w,h,texture);
                        if(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)
                            throw new IllegalStateException("HUD transfer did not restore GL framebuffer/scissor");
                        expect(texture,1,1,frame%2==0?0x80000080:0x40000080);
                        expect(texture,w-1,h-1,0x0040ffff);
                        expect(texture,w-1,1,0);
                    }
                    // Both publications happen before either readback. Queued Vulkan
                    // HUD layers must retain different snapshots of the same GL FBO.
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{1,0,0,1});
                    NativeInterop.publishHud(target.frameBufferId,w,h,texture);
                    GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,1,1});
                    NativeInterop.publishHud(target.frameBufferId,w,h,secondTexture);
                    expect(texture,1,1,0xff0000ff);
                    expect(secondTexture,1,1,0x0000ffff);
                } finally {target.destroyBuffers();}
            }
            System.out.println("[Hybrid] HUD GL-to-registered-Vulkan texture PASS: 30 transfers, 3 sizes, independent ordered snapshots, orientation/transparent/premultiplied pixels, GL state restoration");
        } finally {
            NativeInterop.closeHud();TextureUtil.releaseTextureId(texture);
            TextureUtil.releaseTextureId(secondTexture);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL11.glScissor(box[0],box[1],box[2],box[3]);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    private static void expect(int texture,int x,int y,int expected) {
        int actual=NativeInterop.readHudPixel(texture,x,y);
        for(int shift:new int[]{0,8,16,24})if(Math.abs(((actual>>>shift)&255)-((expected>>>shift)&255))>1)
            throw new IllegalStateException("HUD Vulkan pixel mismatch at "+x+","+y+": "+Integer.toHexString(actual)+" expected "+Integer.toHexString(expected));
    }
}
