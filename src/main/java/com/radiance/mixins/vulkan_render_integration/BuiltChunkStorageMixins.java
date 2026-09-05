package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public class BuiltChunkStorageMixins {

    @Shadow
    protected int sizeY;

    @Shadow
    protected int sizeX;

    @Shadow
    protected int sizeZ;

    @Shadow
    protected World world;

    @Inject(method = "clear()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
    }

    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.setStorage((BuiltChunkStorage) (Object) this);
        ChunkProxy.init(i, sizeX, sizeY, sizeZ, world.getBottomSectionCoord());
        ChunkProxy.setStorage((BuiltChunkStorage) (Object) this);
        return i;
    }

    @Inject(method = "updateCameraPosition(DD)V",
        at = @At(value = "HEAD"))
    private void updateChunkStorageSectionPos(double x, double z, CallbackInfo ci) {
        ChunkProxy.updateSectionPos(ChunkSectionPos.from(net.minecraft.util.math.BlockPos.ofFloored(x, 0, z)));
    }
}
