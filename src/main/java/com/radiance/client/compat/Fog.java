package com.radiance.client.compat;
import net.minecraft.client.render.FogShape;
/** Snapshot of the 1.21.1 RenderSystem fog state for the native uniform buffer. */
public record Fog(float start, float end, FogShape shape, float red, float green, float blue, float alpha) {}
