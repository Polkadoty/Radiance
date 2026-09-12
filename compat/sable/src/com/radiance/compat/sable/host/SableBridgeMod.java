package com.radiance.compat.sable.host;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.LoggerFactory;

@Mod(value="radiance_sable_bridge",dist=Dist.CLIENT)
public final class SableBridgeMod {
    public SableBridgeMod() {
        if (!Boolean.getBoolean("radiance.sableBridge")) return;
        if (Boolean.getBoolean("radiance.syntheticMesh"))
            throw new IllegalStateException("Disable radiance.syntheticMesh before enabling the Sable producer");
        LoggerFactory.getLogger("Radiance Sable").warn("Experimental Vulkan backend: opaque/cutout sublevel blocks only; block entities, transparency and addon section callbacks are not yet rendered");
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (event.getPlayer() != null) Minecraft.getInstance().execute(() -> SableVulkanDispatcher.get().free());
        });
    }
}
