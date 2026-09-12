package com.radiance.compat.sable.producer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copies Radiance's actual 128-byte PBR_QUADS stream to immutable RPM1 packets. No GL calls. */
public final class PbrSectionMeshes {
    public static final int STRIDE=128, MAX_VERTICES=262144;
    public record Material(int texture,int alpha,float emission) {}
    public record Mesh(Material material,ByteBuffer packet,int quads) {
        public Mesh { packet=packet.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN); }
        @Override public ByteBuffer packet() { return packet.duplicate().order(ByteOrder.LITTLE_ENDIAN); }
    }
    public record Result(List<Mesh> meshes,Map<String,Integer> unsupportedQuads) {
        public Result { meshes=List.copyOf(meshes); unsupportedQuads=Map.copyOf(unsupportedQuads); }
    }
    private PbrSectionMeshes() {}
    private static float finite(ByteBuffer b,int at) {
        float f=b.getFloat(at); if(!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite PBR field at "+at); return f;
    }
    private static Material material(ByteBuffer b,int at) {
        int texture=b.getInt(at+76),alpha=b.getInt(at+124);
        float emission=finite(b,at+108);
        if(texture<0 || emission<0 || emission>100)throw new IllegalArgumentException("Invalid PBR material");
        return new Material(texture,alpha,emission==0?0:emission);
    }
    public static Result copy(ByteBuffer source,int vertexCount,int vertexStride) {
        if(vertexStride!=STRIDE)throw new IllegalArgumentException("Expected Radiance PBR128; vanilla BLOCK32 is not compatible");
        if(vertexCount<0 || vertexCount>MAX_VERTICES || vertexCount%4!=0 || source.remaining()!=vertexCount*STRIDE)
            throw new IllegalArgumentException("Invalid PBR QUADS byte/count boundary");
        ByteBuffer b=source.slice().order(ByteOrder.LITTLE_ENDIAN);
        Map<Material,List<float[]>> groups=new LinkedHashMap<>(); Map<String,Integer> unsupported=new LinkedHashMap<>();
        for(int vertex=0;vertex<vertexCount;vertex+=4) {
            int base=vertex*STRIDE; Material key=material(b,base); String reason=null;
            if(key.alpha()!=0 && key.alpha()!=1)reason="transparent_or_custom_alpha";
            for(int v=0;v<4;v++) {
                int at=base+v*STRIDE;
                if(!key.equals(material(b,at)))reason="per_vertex_material";
                if(b.getInt(at+48)==0)reason="untextured_geometry";
                if(b.getInt(at+72)!=0)reason="glint";
                if(b.getInt(at+52)!=0 && (b.getInt(at+64)!=0 || b.getInt(at+68)!=10))reason="nonneutral_overlay";
                if(b.getInt(at+104)!=0 || finite(b,at+112)!=0 || finite(b,at+116)!=0 || finite(b,at+120)!=0)reason="nonlocal_coordinates";
            }
            if(reason!=null) { unsupported.merge(reason,1,Integer::sum); continue; }
            float[] geometric=faceNormal(b,base);
            List<float[]> target=groups.computeIfAbsent(key,k->new ArrayList<>());
            for(int v=0;v<4;v++) {
                int at=base+v*STRIDE; float[] out=new float[12];
                for(int c=0;c<3;c++) { out[c]=finite(b,at+c*4); if(Math.abs(out[c])>1e6)throw new IllegalArgumentException("Local position exceeds native bound"); }
                for(int c=0;c<3;c++)out[3+c]=b.getInt(at+12)==0?geometric[c]:finite(b,at+16+c*4);
                double n=0; for(int c=3;c<6;c++)n+=(double)out[c]*out[c];
                if(n<=1e-12)throw new IllegalArgumentException("Zero PBR normal");
                for(int c=3;c<6;c++)out[c]/=(float)Math.sqrt(n);
                out[6]=finite(b,at+56);out[7]=finite(b,at+60);
                for(int c=0;c<4;c++) { out[8+c]=b.getInt(at+28)==0?1:finite(b,at+32+c*4); if(out[8+c]<0 || out[8+c]>1)throw new IllegalArgumentException("Invalid PBR tint"); }
                target.add(out);
            }
        }
        List<Mesh> result=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            int vc=entry.getValue().size(),ic=vc/4*6;
            ByteBuffer packet=ByteBuffer.allocateDirect(16+vc*48+ic*4).order(ByteOrder.LITTLE_ENDIAN);
            packet.putInt(0x314d5052).putInt(1).putInt(vc).putInt(ic);
            for(float[] v:entry.getValue())for(float f:v)packet.putFloat(f);
            for(int v=0;v<vc;v+=4)for(int i:new int[]{0,1,2,0,2,3})packet.putInt(v+i);
            result.add(new Mesh(entry.getKey(),packet.flip(),vc/4));
        }
        return new Result(result,unsupported);
    }
    private static float[] faceNormal(ByteBuffer b,int at) {
        double[] a=new double[3],c=new double[3];
        for(int i=0;i<3;i++) { a[i]=finite(b,at+STRIDE+i*4)-finite(b,at+i*4);c[i]=finite(b,at+2*STRIDE+i*4)-finite(b,at+i*4); }
        double x=a[1]*c[2]-a[2]*c[1],y=a[2]*c[0]-a[0]*c[2],z=a[0]*c[1]-a[1]*c[0],length=Math.sqrt(x*x+y*y+z*z);
        if(length<=1e-12)throw new IllegalArgumentException("Degenerate section quad");
        return new float[]{(float)(x/length),(float)(y/length),(float)(z/length)};
    }
}
