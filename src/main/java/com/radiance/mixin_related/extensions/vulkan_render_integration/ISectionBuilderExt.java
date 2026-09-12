package com.radiance.mixin_related.extensions.vulkan_render_integration;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.compat.SectionGeometryRenderer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;

public interface ISectionBuilderExt {
    SectionBuilder.RenderData radiance$build(ChunkSectionPos pos, ChunkRendererRegion region,
        VertexSorter sorter, BlockBufferAllocatorStorage buffers, SectionGeometryRenderer.Prepared geometry);
}
