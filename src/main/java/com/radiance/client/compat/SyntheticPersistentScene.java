package com.radiance.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.world.PersistentSceneProxy;
import java.nio.ByteBuffer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;

/** Disabled by default. Two instances share a textured BLAS; one has mirrored nonuniform scale. */
public final class SyntheticPersistentScene {
    private static final boolean ENABLED=Boolean.getBoolean("radiance.syntheticMesh");
    private static final boolean CUTOUT=Boolean.getBoolean("radiance.syntheticMeshCutout");
    private static Object world;
    private static long epoch, start;
    private static double x,y,z;
    private SyntheticPersistentScene() {}
    public static void clear() {
        if(epoch!=0) { RenderSystem.assertOnRenderThread(); PersistentSceneProxy.reset(); }
        epoch=0; world=null;
    }
    public static void render(MinecraftClient client,Camera camera) {
        if(!ENABLED) return;
        RenderSystem.assertOnRenderThread();
        if(client.world==null) { clear(); return; }
        if(world!=client.world || epoch==0) {
            clear(); epoch=PersistentSceneProxy.reset(); world=client.world; start=System.nanoTime();
            x=camera.getPos().x; y=camera.getPos().y; z=camera.getPos().z-4;
            Sprite sprite=client.getBlockRenderManager().getModels().getModelParticleSprite((CUTOUT?Blocks.OAK_LEAVES:Blocks.DIAMOND_BLOCK).getDefaultState());
            int texture=client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId();
            PersistentSceneProxy.material(epoch,1,1,texture,CUTOUT?1:0,0);
            ByteBuffer mesh=SyntheticMeshPackets.cube(sprite.getMinU(),sprite.getMinV(),sprite.getMaxU(),sprite.getMaxV());
            PersistentSceneProxy.mesh(epoch,1,1,1,1,mesh,mesh.remaining());
        }
        ByteBuffer instances=SyntheticMeshPackets.frame((System.nanoTime()-start)/1e9,x,y,z);
        PersistentSceneProxy.instances(epoch,instances,instances.remaining());
    }
}
