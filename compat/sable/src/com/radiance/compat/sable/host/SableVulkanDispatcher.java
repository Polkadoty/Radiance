package com.radiance.compat.sable.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.compat.sable.producer.SablePersistentProducer;
import com.radiance.compat.sable.producer.SablePersistentRenderData;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import java.util.function.Consumer;

/** CPU section production and persistent Vulkan instances; no raster draw stages. */
public final class SableVulkanDispatcher implements SubLevelRenderDispatcher {
    private static final SableVulkanDispatcher INSTANCE = new SableVulkanDispatcher();
    private ClientLevel world;
    private SablePersistentProducer producer;
    private SableVulkanDispatcher() {}
    public static SableVulkanDispatcher get() { return INSTANCE; }

    private SablePersistentProducer forWorld(ClientLevel next) {
        RenderSystem.assertOnRenderThread();
        if (next == null) throw new IllegalStateException("Cannot create Sable render data without a client world");
        if (Boolean.getBoolean("radiance.syntheticMesh"))
            throw new IllegalStateException("Sable and the synthetic mesh test cannot own the same scene snapshot");
        if (next != world) { free(); world = next; }
        if (producer == null) producer = new SablePersistentProducer();
        return producer;
    }

    /** Invoked inside Radiance's world render, once before its native submit. */
    public void frame(Minecraft client) {
        RenderSystem.assertOnRenderThread();
        if (client.level == null) { free(); return; }
        if (world != null && world != client.level) free();
        // Sublevel creation owns lazy allocation; an empty ordinary world needs no native scene.
        if (producer != null) producer.frame(client.getTimer().getGameTimeDeltaPartialTick(false));
    }

    @Override public SubLevelRenderData createRenderData(ClientSubLevel level) {
        return forWorld(level.getLevel()).createRenderData(level);
    }
    @Override public SubLevelRenderData resize(ClientSubLevel level, SubLevelRenderData data) {
        forWorld(level.getLevel());
        if (data instanceof SablePersistentRenderData persistent && data.getSubLevel() == level) {
            persistent.resize(); return persistent;
        }
        if (data != null) data.close();
        return createRenderData(level);
    }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        RenderSystem.assertOnRenderThread();
        if (producer != null) producer.resetResources();
    }
    @Override public void free() {
        RenderSystem.assertOnRenderThread();
        if (producer != null) producer.close();
        producer = null; world = null;
    }
    @Override public void addDebugInfo(Consumer<String> output) {
        output.accept("Radiance Sable: opaque/cutout CPU meshes -> Vulkan");
        output.accept("Sable block entities and transparent ship surfaces: pending integration");
        if (producer != null) output.accept("Sable: " + producer.diagnostics());
    }
    @Override public void updateCulling(Iterable<ClientSubLevel> levels,double x,double y,double z,CullFrustum frustum,boolean spectator) {
        // Retain offscreen geometry for path-traced shadows and reflections.
    }
    @Override public void renderSectionLayer(Iterable<ClientSubLevel> levels,RenderType type,ShaderInstance shader,double x,double y,double z,Matrix4f view,Matrix4f projection,float partialTick) {
        throw new IllegalStateException("OpenGL section stage reached the Radiance Sable dispatcher");
    }
    @Override public void renderAfterSections(Iterable<ClientSubLevel> levels,double x,double y,double z,Matrix4f view,Matrix4f projection,float partialTick) {
        throw new IllegalStateException("OpenGL post-section stage reached the Radiance Sable dispatcher");
    }
    @Override public void renderBlockEntities(Iterable<ClientSubLevel> levels,BlockEntityRenderer renderer,double x,double y,double z,float partialTick) {
        throw new UnsupportedOperationException("Sable block entities require a separate Radiance capture stage");
    }
}
