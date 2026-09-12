package foundry.veil.api.client.render;

import java.util.Map;
import java.util.Set;

/** Startup-only backend selection. Radiance owns geometry submission; no Veil GL renderer exists. */
public final class VeilBackend {
    public enum Backend { OPENGL, RADIANCE }
    private static final Backend SELECTED = parse(System.getProperty("veil.backend", "opengl"));
    private static final Set<String> UNAVAILABLE = Set.of("OpenGL shader programs", "postprocessing and bloom",
            "Veil dynamic lights and shadow maps", "dynamic framebuffer attachments", "perspective rendering",
            "Quasar particle rendering", "Necromancer rendering", "ImGui editors and GPU profiling",
            "Veil custom block layers and texture arrays/cubemaps");
    private static final Map<String, Boolean> RADIANCE_MIXINS = Map.ofEntries(
            Map.entry("foundry.veil.forge.mixin.DeferredRegisterMixin", true),
            Map.entry("foundry.veil.forge.mixin.RegistriesMixin", true),
            Map.entry("foundry.veil.forge.mixin.client.MinecraftMixin", true),
            Map.entry("foundry.veil.forge.mixin.client.RenderBuffersMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.RenderTypeMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.command.ClientCommandSourceStackMixin", true),
            Map.entry("foundry.veil.forge.mixin.client.debug.vanilla.DebugLevelRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.dynamicbuffer.LevelRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.iris.PipelineManagerMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.OcclusionCullerMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.RenderRegionMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.RenderSectionManagerAccessor", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.RenderSectionManagerMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.RenderSectionMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.sodium.SodiumWorldRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.client.perspective.vanilla.LevelRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.iris.IrisRenderTargetMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.iris.IrisRenderingPipelineAccessor", false),
            Map.entry("foundry.veil.forge.mixin.compat.iris.IrisRenderingPipelineMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.iris.ShaderWrapperMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.BlockRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ChunkMeshFormatsMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ChunkShaderOptionsMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ChunkVertexConsumerMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ChunkVertexEncoderVertexMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.DefaultFluidRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.DefaultShaderInterfaceMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.RenderSectionManagerAccessor", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ShaderChunkRendererMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ShaderLoaderMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.ShaderParserMixin", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.SodiumWorldRendererAccessor", false),
            Map.entry("foundry.veil.forge.mixin.compat.sodium.SortedRenderListsAccessor", false),
            Map.entry("foundry.veil.forge.mixin.resources.PathPackResourcesMixin", true),
            Map.entry("foundry.veil.mixin.command.client.ClientCommandSourceMixin", true),
            Map.entry("foundry.veil.mixin.debug.SimpleReloadInstanceMixin", true),
            Map.entry("foundry.veil.mixin.debug.accessor.DebugGameRendererAccessor", false),
            Map.entry("foundry.veil.mixin.debug.accessor.DebugLevelRendererAccessor", false),
            Map.entry("foundry.veil.mixin.debug.accessor.DebugPostChainAccessor", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugActiveProfilerMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugAutoStorageIndexBufferMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugGlDebugMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugKeyboardHandlerMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugLevelRendererMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugMainMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugMinecraftMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugTextureManagerMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugVertexBufferMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.DebugVertexFormatMixin", false),
            Map.entry("foundry.veil.mixin.debug.client.profiler.GameRendererMixin", false),
            Map.entry("foundry.veil.mixin.dynamicbuffer.accessor.DynamicBufferGameRendererAccessor", false),
            Map.entry("foundry.veil.mixin.dynamicbuffer.client.DynamicBufferLevelRendererMixin", false),
            Map.entry("foundry.veil.mixin.fix.MemUtilMixin", true),
            Map.entry("foundry.veil.mixin.fix.NativeImageMixin", true),
            Map.entry("foundry.veil.mixin.fix.WindowMixin", false),
            Map.entry("foundry.veil.mixin.framebuffer.accessor.FramebufferRenderTargetAccessor", false),
            Map.entry("foundry.veil.mixin.necromancer.client.NecromancerClientLevelMixin", false),
            Map.entry("foundry.veil.mixin.necromancer.client.NecromancerLevelRendererMixin", false),
            Map.entry("foundry.veil.mixin.network.NetworkMinecraftServerMixin", true),
            Map.entry("foundry.veil.mixin.network.NetworkServerLevelMixin", true),
            Map.entry("foundry.veil.mixin.network.NetworkServerPlayerMixin", true),
            Map.entry("foundry.veil.mixin.performance.client.PerformanceAbstractTextureMixin", false),
            Map.entry("foundry.veil.mixin.performance.client.PerformanceLevelRendererMixin", false),
            Map.entry("foundry.veil.mixin.performance.client.PerformanceRenderTargetMixin", false),
            Map.entry("foundry.veil.mixin.performance.client.PerformanceScreenEffectRendererMixin", false),
            Map.entry("foundry.veil.mixin.perspective.accessor.GameRendererAccessor", false),
            Map.entry("foundry.veil.mixin.perspective.accessor.LevelRendererAccessor", false),
            Map.entry("foundry.veil.mixin.perspective.client.FrustumMixin", true),
            Map.entry("foundry.veil.mixin.pipeline.accessor.PipelineBufferSourceAccessor", true),
            Map.entry("foundry.veil.mixin.pipeline.accessor.PipelineNativeImageAccessor", true),
            Map.entry("foundry.veil.mixin.pipeline.accessor.PipelineReloadableResourceManagerAccessor", true),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineAbstractTextureMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineAutoStorageIndexBufferMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineDebugScreenOverlayMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineFrustumMixin", true),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineGameRendererMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineGlStateManagerMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineLevelRendererMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineMinecraftMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineParticleRenderTypeMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelinePoseStackMixin", true),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineRenderSystemMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineRenderTargetMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineShaderInstanceMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineTextureManagerMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineVertexBufferMixin", false),
            Map.entry("foundry.veil.mixin.pipeline.client.PipelineWindowMixin", false),
            Map.entry("foundry.veil.mixin.quasar.client.QuasarEntityMixin", false),
            Map.entry("foundry.veil.mixin.quasar.client.QuasarParticleEngineMixin", false),
            Map.entry("foundry.veil.mixin.registry.accessor.RegistryDataAccessor", true),
            Map.entry("foundry.veil.mixin.rendertype.accessor.RenderStateShardAccessor", true),
            Map.entry("foundry.veil.mixin.rendertype.accessor.RenderTypeAccessor", true),
            Map.entry("foundry.veil.mixin.rendertype.accessor.RenderTypeBufferSourceAccessor", true),
            Map.entry("foundry.veil.mixin.rendertype.client.CompositeStateBuilderMixin", true),
            Map.entry("foundry.veil.mixin.rendertype.client.CompositeStateMixin", true),
            Map.entry("foundry.veil.mixin.rendertype.client.RenderTypeMixin", false),
            Map.entry("foundry.veil.mixin.resource.ResourceVanillaPackResourcesMixin", true),
            Map.entry("foundry.veil.mixin.resource.accessor.ResourceAtlasSetAccessor", true),
            Map.entry("foundry.veil.mixin.resource.accessor.ResourceModelManagerAccessor", true),
            Map.entry("foundry.veil.mixin.resource.accessor.ResourceTextureAtlasAccessor", true),
            Map.entry("foundry.veil.mixin.resource.client.ResourceTextureAtlasHolderMixin", true),
            Map.entry("foundry.veil.mixin.resource.client.ResourceTextureAtlasMixin", true),
            Map.entry("foundry.veil.mixin.scheduler.MinecraftServerMixin", true),
            Map.entry("foundry.veil.mixin.shader.client.ShaderEffectInstanceMixin", false),
            Map.entry("foundry.veil.mixin.shader.client.ShaderGameRendererMixin", false),
            Map.entry("foundry.veil.mixin.shader.client.ShaderInstanceMixin", false),
            Map.entry("foundry.veil.mixin.shader.client.ShaderProgramManagerMixin", false),
            Map.entry("foundry.veil.mixin.shader_recompile.accessor.ShaderRecompileProgramAccessor", false),
            Map.entry("foundry.veil.mixin.shader_recompile.client.ShaderRecompileGameRendererMixin", false),
            Map.entry("foundry.veil.mixin.shader_recompile.client.ShaderRecompileShaderInstanceMixin", false)
    );

    private VeilBackend() {}
    public static Backend parse(String value) {
        return switch (value) {
            case "opengl" -> Backend.OPENGL;
            case "radiance" -> Backend.RADIANCE;
            default -> throw new IllegalArgumentException("Unknown veil.backend '" + value + "'; expected opengl or radiance");
        };
    }
    public static Backend selected() { return SELECTED; }
    public static boolean hasOpenGlRenderer() { return SELECTED == Backend.OPENGL; }
    public static Set<String> unavailableFeatures() { return hasOpenGlRenderer() ? Set.of() : UNAVAILABLE; }
    public static void requireOpenGl(String operation) {
        if (!hasOpenGlRenderer()) throw new UnsupportedOperationException(operation
                + " requires Veil's OpenGL renderer; selected backend is Radiance. "
                + "Use the explicit Vulkan geometry bridge; this operation has not been ported.");
    }
    /** An upgrade must be audited instead of silently enabling an unknown GL hook. */
    public static boolean shouldApplyMixin(String name) {
        if (hasOpenGlRenderer()) return true;
        Boolean enabled = RADIANCE_MIXINS.get(name);
        if (enabled == null) throw new IllegalStateException("Unaudited Veil mixin for Radiance backend: " + name);
        return enabled;
    }
}
