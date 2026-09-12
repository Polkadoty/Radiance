// SPDX-License-Identifier: GPL-3.0-only
package com.radiance.hybrid;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.*;
import org.lwjgl.opengl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Ordered compatibility layers around the native Vulkan hotbar. */
public final class HudCompositor {
    private static TextureTarget target;
    private static RenderTarget previousMain;
    private static ShaderInstance shader;
    private static int outputTexture,oldDraw,oldRead;
    // Separate destinations are essential: native overlay commands execute later,
    // so publishing both layers into one texture would overwrite the first layer.
    private static int beforeHotbarTexture;
    private static boolean nativeHotbar,hotbarSeen;
    private static final boolean NATIVE_HOTBAR=Boolean.parseBoolean(System.getProperty("radiance.nativeHotbar","true"));
    private static long nativeDraws,compatibilityDraws,reportAt;
    public static boolean isNativeHotbar(){return nativeHotbar;}
    public static void recordHudDraw(boolean vulkan){
        if(!active)return;
        if(vulkan&&nativeHotbar)nativeDraws++;
        else if(!vulkan)compatibilityDraws++;
    }
    private static final int[] oldViewport=new int[4];
    private static boolean active,logged;
    private static int diagnosticFrames;
    private static int lastDiagnosticAlpha=-1;
    private static boolean sceneEffect;
    private static int sceneDraw,sceneRead;
    private HudCompositor() {}
    public static void beginNativeHotbar(GuiGraphics graphics) {
        if(!active||!NATIVE_HOTBAR)return;
        if(nativeHotbar||sceneEffect||hotbarSeen)throw new IllegalStateException("Unexpected nested/repeated hotbar render");
        graphics.flush();
        NativeInterop.publishHud(target.frameBufferId,target.width,target.height,beforeHotbarTexture);
        composite(beforeHotbarTexture);
        // Only reset color. Keep compatibility depth/stencil for later mod layers.
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,target.frameBufferId);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
            try {
                GL11.glColorMask(true,true,true,true);
                GL30.glClearBufferfv(GL11.GL_COLOR,0,new float[]{0,0,0,0});
            } finally {GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);}
        } finally {if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
        ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(previousMain);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
        nativeHotbar=true;hotbarSeen=true;
    }
    public static void endNativeHotbar(GuiGraphics graphics) {
        if(!nativeHotbar)return;
        try {graphics.flush();} finally {
            nativeHotbar=false;
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(target);
            target.bindWrite(true);
        }
    }
    // The vignette is the first vanilla camera effect. Its ZERO / ONE_MINUS_SRC_COLOR
    // blend multiplies the scene, so it cannot be flattened into transparent HUD RGBA.
    // Keep it on the native scene; resume the GL layer for the subsequent HUD draws.
    public static void beginSceneEffect(GuiGraphics graphics) {
        if(!active)return;
        if(sceneEffect)throw new IllegalStateException("Nested HUD scene effect");
        graphics.flush();
        sceneDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        sceneRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(previousMain);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
        sceneEffect=true;
    }
    public static void endSceneEffect(GuiGraphics graphics) {
        if(!sceneEffect)return;
        try{graphics.flush();}finally {
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(target);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,sceneDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,sceneRead);
            sceneEffect=false;
        }
    }
    public static void begin(GuiGraphics graphics) {
        if(!HybridContext.ENABLED)return;
        HybridContext.assertCurrent();
        if(active)throw new IllegalStateException("Nested HUD composition scope");
        graphics.flush();
        var mc=Minecraft.getInstance();int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
        oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,oldViewport);
        if(target==null) {
            target=new TextureTarget(w,h,true,false);target.enableStencil();target.setClearColor(0,0,0,0);
            outputTexture=TextureUtil.generateTextureId();TextureUtil.prepareImage(outputTexture,0,w,h);
            if(NATIVE_HOTBAR){beforeHotbarTexture=TextureUtil.generateTextureId();TextureUtil.prepareImage(beforeHotbarTexture,0,w,h);}
        } else if(target.width!=w||target.height!=h) {
            target.resize(w,h,false);TextureUtil.prepareImage(outputTexture,0,w,h);
            if(beforeHotbarTexture!=0)TextureUtil.prepareImage(beforeHotbarTexture,0,w,h);
        }
        previousMain=((HybridMainTargetAccess)mc).hybrid$swapMainTarget(target);
        active=true;hotbarSeen=false;
        // Clear must not inherit a mod's scissor or color/depth write mask.
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        target.clear(false);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
        target.bindWrite(true);
        diagnoseAlpha("after HUD clear");
    }
    public static void diagnoseAlpha(String stage) {
        if(!active||diagnosticFrames!=0||!Boolean.getBoolean("radiance.hybridSelfTest")||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)return;
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target.frameBufferId);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            var pixel=stack.malloc(4);GL11.glReadPixels(target.width/2,target.height/4,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            int alpha=Byte.toUnsignedInt(pixel.get(3));
            if(alpha!=lastDiagnosticAlpha)System.out.println("[Hybrid] HUD alpha transition "+lastDiagnosticAlpha+" -> "+alpha+" at "+stage+" blend="+GL11.glIsEnabled(GL11.GL_BLEND)+" src="+GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)+" dst="+GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
            lastDiagnosticAlpha=alpha;
        } finally {GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);}
    }
    public static void end(GuiGraphics graphics) {
        if(!HybridContext.ENABLED||!active)return;
        endNativeHotbar(graphics);
        try {
            graphics.flush();
            NativeInterop.publishHud(target.frameBufferId,target.width,target.height,outputTexture);
            if(Boolean.getBoolean("radiance.hybridSelfTest")&&diagnosticFrames++<3)
                System.out.println("[Hybrid] HUD clear-region RGBA="+Integer.toHexString(NativeInterop.readHudPixel(outputTexture,target.width/2,target.height/4)));
        } finally {
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(previousMain);
            previousMain=null;active=false;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            RenderSystem.viewport(oldViewport[0],oldViewport[1],oldViewport[2],oldViewport[3]);
        }
        composite(outputTexture);
        if(!logged){System.out.println("[Hybrid] Ordered GPU HUD composition active; native Vulkan hotbar="+NATIVE_HOTBAR);logged=true;}
        long now=System.nanoTime();
        if(now-reportAt>30_000_000_000L){
            System.out.println("[Hybrid] HUD routing totals: native-hotbar draws="+nativeDraws+", compatibility draws="+compatibilityDraws+", snapshot textures="+(beforeHotbarTexture==0?1:2));
            reportAt=now;
        }
    }
    private static void composite(int texture) {
        if(shader==null)shader=createShader();
        var oldShader=RenderSystem.getShader();int oldTexture=RenderSystem.getShaderTexture(0);
        int[] caps={GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_COLOR_LOGIC_OP,GL11.GL_STENCIL_TEST};
        boolean[] enabled=new boolean[caps.length];for(int i=0;i<caps.length;i++)enabled[i]=GL11.glIsEnabled(caps[i]);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // Use the Vulkan dispatcher even if a preceding mod left a private FBO bound.
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);
        try(ByteBufferBuilder bytes=new ByteBufferBuilder(256)) {
            RenderSystem.viewport(0,0,target.width,target.height);
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableScissor();
            GlStateManager._disableColorLogicOp();GL11.glDisable(GL11.GL_STENCIL_TEST);com.radiance.client.proxy.vulkan.PipelineStateProxy.DepthStencilState.setStencilTestEnable(false);
            RenderSystem.enableBlend();RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,texture);
            var b=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            // Positive Vulkan viewport: -Y is top; GL row zero is the bottom.
            b.addVertex(-1,-1,0).setUv(0,1);b.addVertex(-1,1,0).setUv(0,0);
            b.addVertex(1,1,0).setUv(1,0);b.addVertex(1,-1,0).setUv(1,1);
            BufferUploader.drawWithShader(b.buildOrThrow());
        } finally {
            RenderSystem.setShader(()->oldShader);RenderSystem.setShaderTexture(0,oldTexture);
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);RenderSystem.blendEquation(equation);
            if(enabled[0])RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(enabled[1])RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(enabled[2])RenderSystem.enableCull();else RenderSystem.disableCull();
            if(enabled[3])GlStateManager._enableScissorTest();else GlStateManager._disableScissorTest();
            if(enabled[4])GlStateManager._enableColorLogicOp();else GlStateManager._disableColorLogicOp();
            if(enabled[5])GL11.glEnable(GL11.GL_STENCIL_TEST);else GL11.glDisable(GL11.GL_STENCIL_TEST);
            com.radiance.client.proxy.vulkan.PipelineStateProxy.DepthStencilState.setStencilTestEnable(enabled[5]);
            RenderSystem.depthMask(depthMask);
            RenderSystem.viewport(oldViewport[0],oldViewport[1],oldViewport[2],oldViewport[3]);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }
    private static ShaderInstance createShader() {
        ResourceProvider resources=id->{
            String text=switch(id.getPath()) {
                case "shaders/core/hybrid_hud.json" -> """
                    {"vertex":"hybrid_hud","fragment":"hybrid_hud","samplers":[{"name":"Sampler0"}],"uniforms":[]}
                    """;
                case "shaders/core/hybrid_hud.vsh" -> """
                    #version 150
                    in vec3 Position;
                    in vec2 UV0;
                    out vec2 texCoord;
                    void main(){gl_Position=vec4(Position,1.0);texCoord=UV0;}
                    """;
                case "shaders/core/hybrid_hud.fsh" -> """
                    #version 150
                    uniform sampler2D Sampler0;
                    in vec2 texCoord;
                    out vec4 fragColor;
                    void main(){fragColor=texture(Sampler0,texCoord);}
                    """;
                default -> null;
            };
            return text==null?Optional.empty():Optional.of(new Resource(null,()->new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))){
                @Override public String sourcePackId(){return "radiance-hybrid-hud";}
            });
        };
        try{return new ShaderInstance(resources,"hybrid_hud",DefaultVertexFormat.POSITION_TEX);}
        catch(IOException failure){throw new IllegalStateException("Cannot load HUD composition shader",failure);}
    }
    public static void close() {
        if(active) {
            ((HybridMainTargetAccess)Minecraft.getInstance()).hybrid$swapMainTarget(previousMain);previousMain=null;active=false;
        }
        NativeInterop.closeHud();
        if(target!=null){target.destroyBuffers();target=null;}
        if(shader!=null){shader.close();shader=null;}
        if(outputTexture!=0){TextureUtil.releaseTextureId(outputTexture);outputTexture=0;}
        if(beforeHotbarTexture!=0){TextureUtil.releaseTextureId(beforeHotbarTexture);beforeHotbarTexture=0;}
        nativeHotbar=false;sceneEffect=false;hotbarSeen=false;
    }
}
