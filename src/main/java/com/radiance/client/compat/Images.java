package com.radiance.client.compat;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.ColorHelper;
/** NativeImage in 1.21.1 exposes ABGR; Radiance's CPU texture algorithms use ARGB. */
public final class Images {
 private Images() {}
 public static int getArgb(NativeImage image,int x,int y){return ColorHelper.Abgr.toAbgr(image.getColor(x,y));}
 public static void setArgb(NativeImage image,int x,int y,int argb){image.setColor(x,y,ColorHelper.Abgr.toAbgr(argb));}
}
