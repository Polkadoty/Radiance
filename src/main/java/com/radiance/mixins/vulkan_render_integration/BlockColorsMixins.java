package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.util.BlockColorEmissionProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockColors.class)
public class BlockColorsMixins implements IBlockColorsExt {
    // Track registrations through the public API: NeoForge replaces vanilla's internal IdList.
    @Unique
    private final Map<Block, BlockColorEmissionProvider> radiance$emissionProviders = new IdentityHashMap<>();

    @Inject(method = "registerColorProvider", at = @At("TAIL"))
    private void radiance$trackEmissionProvider(BlockColorProvider provider, Block[] blocks, CallbackInfo ci) {
        for (Block block : blocks) {
            if (provider instanceof BlockColorEmissionProvider emissionProvider) {
                radiance$emissionProviders.put(block, emissionProvider);
            } else {
                radiance$emissionProviders.remove(block);
            }
        }
    }

    @Override
    public float radiance$getEmission(BlockState state, @Nullable BlockRenderView world,
            @Nullable BlockPos pos, int tintIndex) {
        BlockColorEmissionProvider provider = radiance$emissionProviders.get(state.getBlock());
        return provider == null ? 0.0F : provider.getEmission(state, world, pos, tintIndex);
    }
}