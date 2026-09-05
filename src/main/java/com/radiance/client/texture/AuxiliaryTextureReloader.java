package com.radiance.client.texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;

public class AuxiliaryTextureReloader implements ResourceReloader {

    @Override
    public CompletableFuture<Void> reload(Synchronizer synchronizer, ResourceManager manager,
        net.minecraft.util.profiler.Profiler prepareProfiler, net.minecraft.util.profiler.Profiler applyProfiler,
        Executor prepareExecutor, Executor applyExecutor) {
        return AuxiliaryTextures.prepareDecodedImagesAsync(manager, prepareExecutor)
            .thenCompose(synchronizer::whenPrepared)
            .thenAcceptAsync(AuxiliaryTextures::applyPreparedImages, applyExecutor);
    }
}
