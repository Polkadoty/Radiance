package com.radiance.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;

/** Native initialization runs immediately before renderer creation on both loaders. */
@Mod(value = "radiance", dist = Dist.CLIENT)
public final class RadianceNeoForge {
    public RadianceNeoForge() {
        NeoForgeSectionModels.install();
    }
}
