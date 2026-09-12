package com.radiance.compat.sable.producer.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** CPU cache state only; no OpenGL fields or renderer calls. */
@Mixin(ModelBlockRenderer.class)
public interface ModelCacheAccess {
    @Accessor("CACHE")
    static ThreadLocal<?> radianceSableCache() {
        throw new IllegalStateException("Required producer cache accessor mixin was not applied");
    }
}
