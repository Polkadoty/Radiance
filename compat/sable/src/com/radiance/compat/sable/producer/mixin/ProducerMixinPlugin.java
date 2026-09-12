package com.radiance.compat.sable.producer.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Loading the companion cannot change Minecraft unless the experimental bridge is explicitly enabled. */
public final class ProducerMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin) {return Boolean.getBoolean("radiance.sableBridge");}
    @Override public void acceptTargets(Set<String> ours,Set<String> others) {}
    @Override public List<String> getMixins() {return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
