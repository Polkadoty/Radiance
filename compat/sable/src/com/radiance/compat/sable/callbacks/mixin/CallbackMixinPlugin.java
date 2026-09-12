package com.radiance.compat.sable.callbacks.mixin;

import com.radiance.compat.sable.callbacks.SableRenderFeatureBoundary;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CallbackMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String name) { SableRenderFeatureBoundary.validateBackend(); }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return SableRenderFeatureBoundary.enabled(); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
