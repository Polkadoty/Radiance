package com.radiance.compat.dh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Marks actual exposed DH water sheets across buffer boundaries; never invents a sea plane. */
public final class LodWaterSurface {
    public static final int TOP_VERTEX = 0x4000; // DH3.2 uses only metadata bits0..13.
    private LodWaterSurface() {}
    private record Sheet(int minX, int maxX, int y, int minZ, int maxZ) {}
    private static final class Node {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        Node left,right; List<Sheet> leaves;
        Node(List<Sheet> sheets) {
            for (Sheet s:sheets) { minX=Math.min(minX,s.minX);maxX=Math.max(maxX,s.maxX);
                minY=Math.min(minY,s.y);maxY=Math.max(maxY,s.y);minZ=Math.min(minZ,s.minZ);maxZ=Math.max(maxZ,s.maxZ); }
            if (sheets.size()<=8) { leaves=List.copyOf(sheets);return; }
            boolean splitX=maxX-minX>=maxZ-minZ;
            sheets.sort(Comparator.comparingInt(s->splitX?s.minX+s.maxX:s.minZ+s.maxZ));
            int m=sheets.size()/2;left=new Node(new ArrayList<>(sheets.subList(0,m)));
            right=new Node(new ArrayList<>(sheets.subList(m,sheets.size())));
        }
        boolean contains(int x,int y,int z) {
            if(x<minX||x>maxX||y<minY||y>maxY||z<minZ||z>maxZ)return false;
            if(leaves==null)return left.contains(x,y,z)||right.contains(x,y,z);
            for(Sheet s:leaves)if(y==s.y&&x>=s.minX&&x<=s.maxX&&z>=s.minZ&&z<=s.maxZ)return true;
            return false;
        }
    }
    public static List<LodWaterParts.Part> mark(List<LodWaterParts.Part> parts) {
        List<Sheet> sheets=new ArrayList<>();
        for(var part:parts)if(part.flags()==LodPacket.WATER) {
            ByteBuffer b=part.snapshot().vertices().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            for(int q=b.position();q<b.limit();q+=64) {
                for(int i=0;i<4;i++)if((Short.toUnsignedInt(b.getShort(q+16*i+6))&0xc000)!=0)
                    throw new IllegalArgumentException("DH reserved metadata bits are occupied");
                if(b.get(q+13)!=1)continue;
                int y=Short.toUnsignedInt(b.getShort(q+2));
                int minX=65535,maxX=0,minZ=65535,maxZ=0;
                for(int i=0;i<4;i++) { int p=q+i*16;
                    if(Short.toUnsignedInt(b.getShort(p+2))!=y)throw new IllegalArgumentException("Non-planar DH water top");
                    int x=Short.toUnsignedInt(b.getShort(p)),z=Short.toUnsignedInt(b.getShort(p+4));
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                }
                if(y>0&&minX<maxX&&minZ<maxZ)sheets.add(new Sheet(minX,maxX,y,minZ,maxZ));
            }
        }
        if(sheets.isEmpty())return List.copyOf(parts);
        Node root=new Node(sheets);List<LodWaterParts.Part> result=new ArrayList<>();
        for(var part:parts) {
            if(part.flags()!=LodPacket.WATER){result.add(part);continue;}
            ByteBuffer source=part.snapshot().vertices().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer copy=null;
            for(int p=source.position();p<source.limit();p+=16) {
                if(source.get(p+13)==0)continue; // actual bottom faces remain unchanged
                if(!root.contains(Short.toUnsignedInt(source.getShort(p)),Short.toUnsignedInt(source.getShort(p+2)),
                    Short.toUnsignedInt(source.getShort(p+4))))continue;
                if(copy==null) { copy=ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.LITTLE_ENDIAN);copy.put(source.duplicate()).flip(); }
                int dst=p-source.position();copy.putShort(dst+6,(short)(Short.toUnsignedInt(source.getShort(p+6))|TOP_VERTEX));
            }
            if(copy==null){result.add(part);continue;}
            result.add(new LodWaterParts.Part(new CapturedLodBuffer.Snapshot(part.snapshot().revision(),
                copy.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)),LodPacket.WATER_SURFACE));
        }
        return List.copyOf(result);
    }
}
