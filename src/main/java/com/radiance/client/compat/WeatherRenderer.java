package com.radiance.client.compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/** Emits precipitation into Radiance's post-world geometry queue on 1.21.1. */
public final class WeatherRenderer {
 public static void renderPrecipitation(ClientWorld world, VertexConsumerProvider buffers,int ticks,float delta,Vec3d camera){
  float rain=world.getRainGradient(delta); if(rain<=0) return;
  int radius=MinecraftClient.isFancyGraphicsOrBetter()?10:5;
  int cx=MathHelper.floor(camera.x),cy=MathHelper.floor(camera.y),cz=MathHelper.floor(camera.z);
  for(int z=cz-radius;z<=cz+radius;z++) for(int x=cx-radius;x<=cx+radius;x++) {
   int floor=world.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z);
   int low=Math.max(cy-radius,floor),high=Math.max(cy+radius,floor);if(low==high)continue;
   var pos=new BlockPos(x,Math.max(cy,floor),z);
   var precipitation=world.getBiome(pos).value().getPrecipitation(pos);if(precipitation==Biome.Precipitation.NONE)continue;
   boolean snow=precipitation==Biome.Precipitation.SNOW;
   var texture=Identifier.ofVanilla("textures/environment/"+(snow?"snow":"rain")+".png");
   var vertex=buffers.getBuffer(RenderLayer.getEntityTranslucent(texture));
   double dx=x+0.5-camera.x,dz=z+0.5-camera.z,len=Math.sqrt(dx*dx+dz*dz);
   float ox=len<0.001?0.5f:(float)(-dz/len*0.5),oz=len<0.001?0:(float)(dx/len*0.5);
   float alpha=(float)Math.max(0,1-len*len/(radius*radius)) * rain * (snow?0.8f:0.6f);
   int light=WorldRenderer.getLightmapCoordinates(world,pos);
   long seed=(long)x*x*3121+(long)x*45238971+(long)z*z*418711+(long)z*13761;
   float scroll=-(ticks+delta)*(snow?0.01f:0.03f)+(seed&255)/256f;
   float px=(float)dx,pz=(float)dz,y1=(float)(low-camera.y),y2=(float)(high-camera.y);
   vertex.vertex(px-ox,y2,pz-oz).texture(0,low*.25f+scroll).color(1f,1f,1f,alpha).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,1,0);
   vertex.vertex(px+ox,y2,pz+oz).texture(1,low*.25f+scroll).color(1f,1f,1f,alpha).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,1,0);
   vertex.vertex(px+ox,y1,pz+oz).texture(1,high*.25f+scroll).color(1f,1f,1f,alpha).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,1,0);
   vertex.vertex(px-ox,y1,pz-oz).texture(0,high*.25f+scroll).color(1f,1f,1f,alpha).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,1,0);
  }
 }
 public static void renderBorder(ClientWorld world,VertexConsumerProvider buffers,Vec3d camera){
  var border=world.getWorldBorder();int range=MinecraftClient.getInstance().options.getClampedViewDistance()*16;
  double distance=border.getDistanceInsideBorder(camera.x,camera.z);if(distance>range)return;
  int color=border.getStage().getColor();float alpha=(float)Math.pow(1-Math.max(0,distance)/range,4);
  var v=buffers.getBuffer(RenderLayer.getEntityTranslucent(Identifier.ofVanilla("textures/misc/forcefield.png")));
  double minX=Math.max(border.getBoundWest(),camera.x-range),maxX=Math.min(border.getBoundEast(),camera.x+range);
  double minZ=Math.max(border.getBoundNorth(),camera.z-range),maxZ=Math.min(border.getBoundSouth(),camera.z+range);
  if(Math.abs(border.getBoundWest()-camera.x)<range) wall(v,minX,minZ,minX,maxZ,camera,color,alpha,range);
  if(Math.abs(border.getBoundEast()-camera.x)<range) wall(v,maxX,maxZ,maxX,minZ,camera,color,alpha,range);
  if(Math.abs(border.getBoundNorth()-camera.z)<range) wall(v,maxX,minZ,minX,minZ,camera,color,alpha,range);
  if(Math.abs(border.getBoundSouth()-camera.z)<range) wall(v,minX,maxZ,maxX,maxZ,camera,color,alpha,range);
 }
 private static void wall(VertexConsumer v,double x1,double z1,double x2,double z2,Vec3d c,int color,float a,int height){
  float r=Colors.getRedFloat(color),g=Colors.getGreenFloat(color),b=Colors.getBlueFloat(color),u=(float)Math.hypot(x2-x1,z2-z1)*.5f;
  v.vertex((float)(x1-c.x),-height,(float)(z1-c.z)).texture(0,0).color(r,g,b,a).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0,1,0);
  v.vertex((float)(x2-c.x),-height,(float)(z2-c.z)).texture(u,0).color(r,g,b,a).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0,1,0);
  v.vertex((float)(x2-c.x),height,(float)(z2-c.z)).texture(u,height).color(r,g,b,a).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0,1,0);
  v.vertex((float)(x1-c.x),height,(float)(z1-c.z)).texture(0,height).color(r,g,b,a).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0,1,0);
 }
}
