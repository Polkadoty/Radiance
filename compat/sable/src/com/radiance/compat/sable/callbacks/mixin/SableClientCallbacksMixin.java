package com.radiance.compat.sable.callbacks.mixin;

import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import dev.ryanhcode.sable.SableClient;
import foundry.veil.api.event.VeilAddShaderPreProcessorsEvent;
import foundry.veil.api.event.VeilRendererAvailableEvent;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import static com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary.Feature.*;

@Mixin(value=SableClient.class, remap=false)
public abstract class SableClientCallbacksMixin {
    @Redirect(method="init()V", at=@At(value="INVOKE", target="Lfoundry/veil/platform/VeilEventPlatform;onVeilRendererAvailable(Lfoundry/veil/api/event/VeilRendererAvailableEvent;)V"), require=1)
    private static void radiance$editor(VeilEventPlatform platform, VeilRendererAvailableEvent callback) {
        if (SableRenderFeatureBoundary.enabled()) SableRenderFeatureBoundary.omit(VEIL_EDITOR);
        else platform.onVeilRendererAvailable(callback);
    }
    @Redirect(method="init()V", at=@At(value="INVOKE", target="Lfoundry/veil/platform/VeilEventPlatform;onVeilAddShaderProcessors(Lfoundry/veil/api/event/VeilAddShaderPreProcessorsEvent;)V"), require=1)
    private static void radiance$processors(VeilEventPlatform platform, VeilAddShaderPreProcessorsEvent callback) {
        if (SableRenderFeatureBoundary.enabled()) SableRenderFeatureBoundary.omit(VEIL_SHADER_PROCESSORS);
        else platform.onVeilAddShaderProcessors(callback);
    }
    // This exact call in SableClient.init registers only SableSkyLightShadows::renderShadowMap.
    // GIZMO_HANDLER.init() remains in the original method and keeps its own CPU/vanilla draw callback.
    @Redirect(method="init()V", at=@At(value="INVOKE", target="Lfoundry/veil/platform/VeilEventPlatform;onVeilRenderLevelStage(Lfoundry/veil/api/event/VeilRenderLevelStageEvent;)V"), require=1)
    private static void radiance$shadowStage(VeilEventPlatform platform, VeilRenderLevelStageEvent callback) {
        if (SableRenderFeatureBoundary.enabled()) SableRenderFeatureBoundary.omit(SKYLIGHT_SHADOW_MAP);
        else platform.onVeilRenderLevelStage(callback);
    }
}
