package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.UnsafeManager;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import com.radiance.client.compat.CloudRenderer;
import net.minecraft.client.render.DimensionEffects;
import com.radiance.client.compat.Fog;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;



import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    private ClientWorld world;

    @Final
    @Shadow
    private MinecraftClient client;

    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private Frustum frustum;

    @Unique
    private final List<Entity> renderedEntities = new java.util.ArrayList<>();



    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraYaw;

    @Final
    @Shadow
    private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow private int ticks;
    @Unique private final CloudRenderer cloudRenderer = new CloudRenderer();
    @Inject(method={"renderStars","renderLightSky","renderDarkSky"},at=@At("HEAD"),cancellable=true)
    private void radiance$skipSkyBuffers(CallbackInfo ci) { ci.cancel(); }
    @Inject(method="close",at=@At("HEAD"))
    private void radiance$closeClouds(CallbackInfo ci) { com.radiance.client.compat.SyntheticPersistentScene.clear(); cloudRenderer.close(); }
    @Inject(method="reload(Lnet/minecraft/resource/ResourceManager;)V",at=@At("HEAD"))
    private void radiance$reloadClouds(net.minecraft.resource.ResourceManager manager, CallbackInfo ci) { com.radiance.client.compat.SyntheticPersistentScene.clear(); cloudRenderer.reload(manager); }

    @Redirect(method = "scheduleTerrainUpdate()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52817()V"))
    public void cancelTerrainUpdateWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance) {

    }

    // region <close>

    @Redirect(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;loadEntityOutlinePostProcessor()V"))
    public void cancelReloadWithResourceManager(WorldRenderer instance) {

    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52826"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelReloadWithChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }



    // region <render>
    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Unique
    protected boolean getEntitiesToRender(Camera camera, Frustum frustum, List<Entity> output) {
        Vec3d pos=camera.getPos();
        for(Entity entity:world.getEntities()) {
            if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()
                    && !com.radiance.client.option.Options.renderFirstPersonBody) {
                continue;
            }
            if (entity.squaredDistanceTo(pos)<48*48 || entityRenderDispatcher.shouldRender(entity,frustum,pos.x,pos.y,pos.z)
                    || entity.hasPassengerDeep(client.player)) {
                if(entity instanceof net.minecraft.client.network.ClientPlayerEntity && camera.getFocusedEntity()!=entity) continue;
                if(entity.age==0){entity.lastRenderX=entity.getX();entity.lastRenderY=entity.getY();entity.lastRenderZ=entity.getZ();}
                output.add(entity);
            }
        }
        return false;
    }

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    protected abstract void applyFrustum(Frustum frustum);

    @Unique
    protected boolean isSkyDark(float delta) {
        return client.player.getCameraPosVec(delta).y < world.getLevelProperties().getSkyDarknessHeight(world);
    }

    @Shadow
    protected abstract boolean hasBlindnessOrDarkness(Camera camera);

    @Inject(method =
        "render(Lnet/minecraft/client/render/RenderTickCounter;"
            + "ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(RenderTickCounter tickCounter,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        com.radiance.client.compat.SyntheticPersistentScene.render(client, camera);
        PlayerProxy.setCameraPos(camera.getPos());

        float f = tickCounter.getTickDelta(false);
        RenderSystem.setShaderGameTime(this.world.getTime(), f);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Frustum frustum = this.frustum;

        Vec3d vec3d = camera.getPos();
        double x = vec3d.getX();
        double y = vec3d.getY();
        double z = vec3d.getZ();

        this.setupTerrain(camera, frustum, false, false);

        boolean renderEntityOutline = this.getEntitiesToRender(camera, frustum,
            this.renderedEntities);

        Matrix4f viewMatrix = new Matrix4f(
            ((IGameRendererExt) gameRenderer).radiance$getRotationMatrix());
        Matrix4f effectedViewMatrix = new Matrix4f(effectedRotationMatrix);

        // fog
        float h = gameRenderer.getViewDistance();
        boolean bl2 = this.client.world.getDimensionEffects()
            .useThickFog(MathHelper.floor(x), MathHelper.floor(y))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        BackgroundRenderer.render(camera, f, world, client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(f));
        BackgroundRenderer.applyFogColor();
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, Math.max(h,32), bl2, f);
        float[] fogColor=RenderSystem.getShaderFogColor();
        Fog fog=new Fog(RenderSystem.getShaderFogStart(),RenderSystem.getShaderFogEnd(),RenderSystem.getShaderFogShape(),
                fogColor[0],fogColor[1],fogColor[2],fogColor[3]);

        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).radiance$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(EndPortalBlockEntityRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            EndPortalBlockEntityRenderer.PORTAL_TEXTURE).getGlId();
        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) (gameRenderer.getLightmapTextureManager());
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, fog, world, endSkyTextureID, endPortalTextureID,
            lightMapManagerExt.radiance$getTextureId());

        // Sky
        float tickDelta = tickCounter.getTickDelta(false);
        float skyAngle = this.world.getSkyAngle(tickDelta);
        Vec3d skyColor = this.world.getSkyColor(camera.getPos(), tickDelta);
        int baseColor = com.radiance.client.compat.Colors.fromFloats(1,(float)skyColor.x,(float)skyColor.y,(float)skyColor.z);

        DimensionEffects dimensionEffects = this.world.getDimensionEffects();
        float[] horizon = dimensionEffects.getFogColorOverride(skyAngle, tickDelta);
        int horizonColor = horizon == null ? 0 : com.radiance.client.compat.Colors.fromFloats(horizon[3],horizon[0],horizon[1],horizon[2]);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngle * 360.0F));
        Matrix4f rotationMatrix = matrixStack.peek().getPositionMatrix();
        Vector3f sunDirection = rotationMatrix.transformPosition(0, 1, 0, new Vector3f())
            .normalize();
        matrixStack.pop();

        boolean hasBlindnessOrDarkness = this.hasBlindnessOrDarkness(camera);

        int submersionType = camera.getSubmersionType().ordinal();

        int moonPhase = this.world.getMoonPhase();

        float rainGradient = this.world.getRainGradient(tickDelta);

        int sunTextureID = textureManager.getTexture(Identifier.ofVanilla("textures/environment/sun.png")).getGlId();

        int moonTextureID = textureManager.getTexture(Identifier.ofVanilla("textures/environment/moon_phases.png")).getGlId();

        BufferProxy.updateSkyUniform(com.radiance.client.compat.Colors.getRedFloat(baseColor),
            com.radiance.client.compat.Colors.getGreenFloat(baseColor), com.radiance.client.compat.Colors.getBlueFloat(baseColor),
            com.radiance.client.compat.Colors.getRedFloat(horizonColor), com.radiance.client.compat.Colors.getGreenFloat(horizonColor),
            com.radiance.client.compat.Colors.getBlueFloat(horizonColor), com.radiance.client.compat.Colors.getAlphaFloat(horizonColor), sunDirection,
            dimensionEffects.getSkyType().ordinal(), horizon != null,
            this.isSkyDark(tickDelta), hasBlindnessOrDarkness, submersionType, moonPhase,
            rainGradient, sunTextureID, moonTextureID);

        BufferProxy.updateMapping();

        // Entities
        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickCounter, canDrawEntityOutlines());

        Pair<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            chunks, this.noCullingBlockEntities, blockBreakingProgressions,
            blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, blockBreakingProgressions,
            this.client.getBlockRenderManager(), this.world, crumblingRenderData.getLeft(),
            crumblingRenderData.getRight());

        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);

        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, world);
        }

        EntityProxy.queueWeatherBuild(this.world,
            camera, this.ticks, tickDelta);

        // clouds
        CloudRenderMode cloudRenderMode = this.client.options.getCloudRenderModeValue();
        if (cloudRenderMode != CloudRenderMode.OFF) {
            float k = this.world.getDimensionEffects().getCloudsHeight();
            if (!Float.isNaN(k)) {
                float ticks = (float) this.ticks + f;
                Vec3d cloudColor = this.world.getCloudsColor(f);
                int color = com.radiance.client.compat.Colors.fromFloats(1,(float)cloudColor.x,(float)cloudColor.y,(float)cloudColor.z);
                float cloudHeight = k + 0.33F;
                this.cloudRenderer.renderClouds(color, cloudRenderMode, cloudHeight, null, null,
                    camera.getPos(), ticks);
            }
        }

        // Chunks
        ChunkProxy.setStorage(chunks);
        ChunkProxy.rebuild(camera);

        this.renderedEntities.clear();

        // Match vanilla: world fog must not tint the subsequent GUI and text.
        com.radiance.client.compat.DistantHorizonsCompat.render(f, effectedRotationMatrix, projectionMatrix,
            client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
        BackgroundRenderer.clearFog();

        ci.cancel();
    }
    // endregion

    @Inject(method="setWorld", at=@At("HEAD"))
    private void radiance$resetSyntheticScene(ClientWorld newWorld, CallbackInfo ci) {
        com.radiance.client.compat.SyntheticPersistentScene.clear();
    }

    // region <setWorld>
    @Redirect(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52826"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelSetWorldChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }
    // endregion

    //region <setupTerrain>
    @Inject(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.AFTER), cancellable = true)
    public void cancelCullAndUpdateWithChunkRenderingDataPreparer(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci) {
this.world.getProfiler().pop();
        ci.cancel();
    }
    //endregion

    // These notifications belong to vanilla's visibility graph, which Vulkan replaces.
    @Redirect(method = "method_52815", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52819(Lnet/minecraft/util/math/ChunkPos;)V"))
    private void radianceSkipVanillaChunkNotification(ChunkRenderingDataPreparer preparer, net.minecraft.util.math.ChunkPos pos) {
    }

    @Redirect(method = "addBuiltChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;method_52827(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"))
    private void radianceSkipVanillaBuiltChunkNotification(ChunkRenderingDataPreparer preparer, ChunkBuilder.BuiltChunk chunk) {
    }
    // region <addBuiltChunk>
    // endregion

    // region <onChunkUnload>
    // endregion

    // region <scheduleNeighborUpdates>
    // endregion

    // region <isRenderingReady>
    @Inject(method = "isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ChunkBuilder.BuiltChunk builtChunk = chunks.getRenderedChunk(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.data.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.data.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }
    // endregion

    // region <>
    @Inject(method = "getCompletedChunkCount()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkNum - 54); // 54 + 10 = 64
    }
    // endregion
}
