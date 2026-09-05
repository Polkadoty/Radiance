package com.radiance.client.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Deterministic CPU producer for the optional GPU acceptance harness. */
public final class SyntheticMeshPackets {
    private SyntheticMeshPackets() {}
    public static ByteBuffer cube(float u0,float v0,float u1,float v1) {
        ByteBuffer b=ByteBuffer.allocateDirect(16+24*48+36*4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x314d5052).putInt(1).putInt(24).putInt(36);
        // Local +/-0.5 cube; per-face vertices preserve hard normal and UV seams.
        for(int axis=0;axis<3;axis++) for(int side=0;side<2;side++) {
            int u=(axis+1)%3,v=(axis+2)%3; float sign=side==0?-1:1;
            for(int corner=0;corner<4;corner++) {
                float[] p=new float[3], n=new float[3]; p[axis]=.5f*sign; n[axis]=sign;
                p[u]=(corner==1||corner==2)? .5f:-.5f; p[v]=corner>=2?.5f:-.5f;
                for(float f:p)b.putFloat(f); for(float f:n)b.putFloat(f);
                b.putFloat(corner==1||corner==2?u1:u0).putFloat(corner>=2?v1:v0);
                b.putFloat(1).putFloat(1).putFloat(1).putFloat(1);
            }
        }
        for(int face=0;face<6;face++) {
            int base=face*4;
            int[] winding=face%2==0?new int[]{0,2,1,0,3,2}:new int[]{0,1,2,0,2,3};
            for(int i:winding)b.putInt(base+i);
        }
        return b.flip();
    }
    public static ByteBuffer frame(double seconds,double x,double y,double z) {
        ByteBuffer b=ByteBuffer.allocateDirect(16+2*84).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x31495052).putInt(1).putInt(2).putInt(0);
        for(int i=0;i<2;i++) {
            double angle=seconds*.4+i*.7; float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
            float sx=i==0?1:-.7f,sy=i==0?1:1.4f,sz=i==0?1:.8f;
            b.putLong(i+1).putLong(1).putLong(1);
            for(float f:new float[]{c*sx,0,s*sz,0,sy,0,-s*sx,0,c*sz})b.putFloat(f);
            b.putDouble(x+i*2).putDouble(y).putDouble(z);
        }
        return b.flip();
    }
}
