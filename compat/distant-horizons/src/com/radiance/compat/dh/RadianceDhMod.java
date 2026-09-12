package com.radiance.compat.dh;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
@Mod(value = "radiance_dh_bridge", dist = Dist.CLIENT)
public final class RadianceDhMod { public RadianceDhMod() { RadianceDhConfig.current = RadianceDhConfig.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("radiance-dh.properties")); } }
