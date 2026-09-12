package com.radiance.compat.dh.mixin;

import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.renderPass.IDhGenericRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree", remap = false)
public abstract class LodQuadTreeMixin {
    // Only this constructor lookup feeds BeaconRenderHandler; returning null takes DH's existing
    // no-beacon-handler branch. Terrain setup and every other generic renderer lookup still run.
    @Redirect(method = "<init>(Lcom/seibel/distanthorizons/core/level/IDhClientLevel;IIILcom/seibel/distanthorizons/core/file/fullDatafile/V2/FullDataSourceProviderV2;)V",
            at = @At(value = "INVOKE", target = "Lcom/seibel/distanthorizons/core/level/IDhClientLevel;getGenericRenderer()Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhGenericRenderer;"),
            remap = false, require = 1, allow = 1)
    private IDhGenericRenderer radiance$omitDhBeaconRenderer(IDhClientLevel level) { return null; }
}
