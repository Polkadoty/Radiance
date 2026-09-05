package com.radiance.client.compat;
public final class Colors {
 private Colors() {}
 public static int getAlpha(int c){return c>>>24;}
 public static int getRed(int c){return c>>>16&255;}
 public static int getGreen(int c){return c>>>8&255;}
 public static int getBlue(int c){return c&255;}
 public static float getAlphaFloat(int c){return getAlpha(c)/255f;}
 public static float getRedFloat(int c){return getRed(c)/255f;}
 public static float getGreenFloat(int c){return getGreen(c)/255f;}
 public static float getBlueFloat(int c){return getBlue(c)/255f;}
 public static int getArgb(int a,int r,int g,int b){return a<<24|r<<16|g<<8|b;}
 public static int withAlpha(int a,int c){return a<<24|c&0xffffff;}
 public static int fromFloats(float a,float r,float g,float b){return getArgb((int)(255*a),(int)(255*r),(int)(255*g),(int)(255*b));}
 public static int mix(int a,int b){return getArgb(getAlpha(a)*getAlpha(b)/255,getRed(a)*getRed(b)/255,getGreen(a)*getGreen(b)/255,getBlue(a)*getBlue(b)/255);}
}
