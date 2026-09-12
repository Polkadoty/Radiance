package com.radiance.compat.dh.mixin;
import com.radiance.compat.dh.RadianceDhViewport;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_neoforge", remap = false)
public abstract class MinecraftRenderWrapperMixin {
    @Unique private static final ILightMapWrapper radiance$lightmap = new ILightMapWrapper() {};
    @Inject(method = "getMcRenderingApi", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$api(CallbackInfoReturnable<EDhApiRenderingApi> ci) { ci.setReturnValue(EDhApiRenderingApi.VULKAN); }
    @Inject(method = "getLightmapWrapper", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$lightmap(CallbackInfoReturnable<ILightMapWrapper> ci) { ci.setReturnValue(radiance$lightmap); }
    @Inject(method = {"updateLightmap", "setLightmapId"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$skipGlLightmap(CallbackInfo ci) { ci.cancel(); }
    @Inject(method = "getTargetFramebufferViewportWidth()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$viewportWidth(CallbackInfoReturnable<Integer> ci) { ci.setReturnValue(RadianceDhViewport.width()); }
    @Inject(method = "getTargetFramebufferViewportHeight()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$viewportHeight(CallbackInfoReturnable<Integer> ci) { ci.setReturnValue(RadianceDhViewport.height()); }
    @Inject(method = "mcRendersToFrameBuffer()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$noGlFramebuffer(CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(false); }
    // The custom backend never composites GL resources. -1 denotes absent, not default framebuffer 0.
    @Inject(method = {"getTargetFramebuffer()I", "getGlDepthTextureId()I", "getGlColorTextureId()I"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void radiance$noGlHandle(CallbackInfoReturnable<Integer> ci) { ci.setReturnValue(-1); }
}
