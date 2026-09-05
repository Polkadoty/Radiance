package com.radiance.client.compat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.shape.VoxelShape;
public final class VertexRendering {
 public static void drawOutline(MatrixStack matrices, VertexConsumer consumer, VoxelShape shape,double x,double y,double z,int color){
   var entry=matrices.peek();
   shape.forEachEdge((x1,y1,z1,x2,y2,z2)->{
     var normal=new org.joml.Vector3f((float)(x2-x1),(float)(y2-y1),(float)(z2-z1)).normalize();
     consumer.vertex(entry,(float)(x+x1),(float)(y+y1),(float)(z+z1)).color(color).normal(entry,normal.x,normal.y,normal.z);
     consumer.vertex(entry,(float)(x+x2),(float)(y+y2),(float)(z+z2)).color(color).normal(entry,normal.x,normal.y,normal.z);
   });
 }
}
