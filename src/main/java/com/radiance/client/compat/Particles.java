package com.radiance.client.compat;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.SpriteAtlasTexture;
public final class Particles {
 public static RenderLayer layer(ParticleTextureSheet sheet){
  var texture=sheet==ParticleTextureSheet.TERRAIN_SHEET?SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE:SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE;
  return sheet==ParticleTextureSheet.PARTICLE_SHEET_OPAQUE || sheet==ParticleTextureSheet.PARTICLE_SHEET_LIT
    ? RenderLayer.getEntityCutoutNoCull(texture) : RenderLayer.getEntityTranslucent(texture);
 }
}
