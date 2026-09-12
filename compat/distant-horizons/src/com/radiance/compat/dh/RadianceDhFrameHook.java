package com.radiance.compat.dh;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.util.math.DhMat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.joml.Matrix4f;

/** Called by Radiance because its world-render replacement skips vanilla renderSectionLayer. */
public final class RadianceDhFrameHook {
    private RadianceDhFrameHook() {}
    public static void render(float partialTick, Matrix4f modelView, Matrix4f projection, int width, int height) {
        if (!RadianceDhViewport.beginFrame(width, height)) return;
        IMinecraftClientWrapper client = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
        if (client == null || client.getWrappedClientLevel() == null) return;
        ClientApi.RENDER_STATE.mcModelViewMatrix = new DhMat4f(modelView);
        ClientApi.RENDER_STATE.mcProjectionMatrix = new DhMat4f(projection);
        ClientApi.RENDER_STATE.partialTickTime = partialTick;
        ClientApi.RENDER_STATE.clientLevelWrapper = client.getWrappedClientLevel();
        ClientApi.RENDER_STATE.vanillaFogEnabled = false; // Radiance composes its own medium fog.
        ClientApi.INSTANCE.renderLods();
    }
}
