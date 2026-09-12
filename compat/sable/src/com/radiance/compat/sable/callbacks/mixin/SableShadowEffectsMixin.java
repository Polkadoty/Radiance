package com.radiance.compat.sable.callbacks.mixin;

import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary.Feature.SKYLIGHT_SHADOW_MAP;

@Mixin(value=SableSkyLightShadows.class, remap=false)
public abstract class SableShadowEffectsMixin {
    @Inject(method={"isEnabled()Z", "renderingShadowMap()Z"}, at=@At("HEAD"), cancellable=true)
    private static void radiance$disabled(CallbackInfoReturnable<Boolean> cir) {
        if (SableRenderFeatureBoundary.enabled()) cir.setReturnValue(false);
    }
    @ModifyVariable(method="setIsEnabled(Z)V", at=@At("HEAD"), argsOnly=true, ordinal=0)
    private static boolean radiance$requested(boolean requested) {
        return SableRenderFeatureBoundary.effectiveEnabled(requested, SKYLIGHT_SHADOW_MAP);
    }
    @Inject(method={"renderShadowMap(Lfoundry/veil/api/event/VeilRenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lfoundry/veil/api/client/render/MatrixStack;Lorg/joml/Matrix4fc;Lorg/joml/Matrix4fc;ILnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", "bindShadowMapTexture(Lnet/minecraft/client/renderer/ShaderInstance;)V"}, at=@At("HEAD"), cancellable=true)
    private static void radiance$skipEffect(CallbackInfo ci) {
        if (SableRenderFeatureBoundary.enabled()) { SableRenderFeatureBoundary.omit(SKYLIGHT_SHADOW_MAP); ci.cancel(); }
    }
    @Inject(method="getShadowsFramebuffer()Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;", at=@At("HEAD"), cancellable=true)
    private static void radiance$noFramebuffer(CallbackInfoReturnable<AdvancedFbo> cir) {
        if (SableRenderFeatureBoundary.enabled()) cir.setReturnValue(null); // This API is explicitly nullable.
    }
}
