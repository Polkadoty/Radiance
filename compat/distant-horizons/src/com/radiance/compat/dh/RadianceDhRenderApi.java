package com.radiance.compat.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiLodShading;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.EDhRenderDepth;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.render.renderer.AbstractDebugWireframeRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.*;
import java.util.List;

/** Pinned DH 3.2.0 backend. Radiance owns all actual world lighting and final composition. */
public final class RadianceDhRenderApi extends AbstractDhRenderApiDefinition {
    public static void install() {
        if (!"3.2.0-b".equals(DhApi.getModVersion()))
            throw new IllegalStateException("Radiance DH bridge requires DH 3.2.0-b, found " + DhApi.getModVersion());
        Config.Client.Advanced.Graphics.Quality.lodShading.setApiValue(EDhApiLodShading.DISABLED);
        Config.Client.Advanced.Graphics.Texture.enableTexturedLods.setApiValue(false);
        Config.Client.Advanced.Graphics.overrideVanillaGraphicsSettings.setApiValue(false);
        Config.Client.Advanced.Graphics.Fog.enableVanillaFog.setApiValue(true);
        Config.Client.Advanced.Graphics.Culling.disableFrustumCulling.setApiValue(true);
        Config.Client.Advanced.Graphics.Culling.disableShadowPassFrustumCulling.setApiValue(true);
        Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.setApiValue(false);
        RadianceDhCloudOwnership.configure();
        RadianceDhBeaconOwnership.configure();
        Config.Client.Advanced.Graphics.enableSsao.setApiValue(false);
        new RadianceDhRenderApi().bindRenderers();
        DhApi.events.bind(DhApiWorldUnloadEvent.class, new DhApiWorldUnloadEvent() {
            @Override public void onWorldUnload(DhApiEventParam<EventParam> event) {
                // MCVR World::close owns native GPU cleanup. Avoid Vulkan/global-world calls on DH worker.
                RadianceDhTerrainRenderer.INSTANCE.clearJavaState();
            }
        });
        System.out.println("[Radiance DH] Installed Vulkan mesh capture backend for DH 3.2.0-b");
    }
    @Override public String getEngineName() { return "Radiance Vulkan LOD prototype"; }
    @Override public EDhRenderDepth getRenderDepth() { return EDhRenderDepth.FORWARD_Z; }
    @Override public EDhApiRenderingApi getRenderApi() { return EDhApiRenderingApi.VULKAN; }
    @Override public boolean isNativeRenderer() { return true; }
    @Override public IDhTerrainRenderer getTerrainRenderer() { return RadianceDhTerrainRenderer.INSTANCE; }
    @Override public IDhMetaRenderer getMetaRenderer() { return new IDhMetaRenderer() {
        public void runRenderPassSetup(RenderParams p) {}
        public void runRenderPassCleanup(RenderParams p) {}
        public void applyToMcTexture(RenderParams p) {}
        public void clearDhDepthAndColorTextures(RenderParams p) {}
    }; }
    @Override public IDhSsaoRenderer getSsaoRenderer() { return p -> {}; }
    @Override public IDhFogRenderer getFogRenderer() { return (p, fog) -> {}; }
    @Override public IDhFarFadeRenderer getFarFadeRenderer() { return p -> {}; }
    @Override public IDhVanillaFadeRenderer getVanillaFadeRenderer() { return p -> {}; }
    @Override public IDhTestTriangleRenderer getTestTriangleRenderer() { return p -> {}; }
    @Override public AbstractDebugWireframeRenderer getDebugWireframeRenderer() { return new AbstractDebugWireframeRenderer() {
        @Override public void renderBox(Box box) {}
        @Override public void render(RenderParams p) {}
    }; }
    @Override public IVertexBufferWrapper createVboWrapper(String name) { return new CapturedLodBuffer(); }
    @Override public ILodContainerUniformBufferWrapper createLodContainerUniformWrapper() { return new ILodContainerUniformBufferWrapper() {
        public void tryUpload(com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer c) {}
        public void close() {}
    }; }
    @Override public IDhGenericRenderer createGenericRenderer() { return new IDhGenericRenderer() {
        public void render(RenderParams p, IProfilerWrapper profiler, boolean ssao) {}
        public String getVboRenderDebugMenuString() { return "Radiance DH: opaque terrain only"; }
        public void close() {}
        public void add(IDhApiRenderableBoxGroup group) { throw new UnsupportedOperationException("DH generic objects are not supported by the Radiance prototype"); }
        public IDhApiRenderableBoxGroup remove(long id) { return null; }
    }; }
    @Override public IDhGenericObjectVertexBufferContainer createGenericVboContainer() { return new IDhGenericObjectVertexBufferContainer() {
        private EState state = EState.NEW;
        public void uploadDataToGpu() { state = EState.ERROR; }
        public void updateVertexData(List<DhApiRenderableBox> boxes) { state = EState.ERROR; }
        public EState getState() { return state; }
        public void setState(EState value) { state = value; }
        public void close() { state = EState.ERROR; }
    }; }
}
