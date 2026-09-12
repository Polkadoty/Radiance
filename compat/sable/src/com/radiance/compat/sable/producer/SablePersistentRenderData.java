package com.radiance.compat.sable.producer;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real CPU section storage. It never creates a vanilla RenderSection or OpenGL VertexBuffer. */
public final class SablePersistentRenderData implements SubLevelRenderData {
    final SablePersistentProducer owner;
    final SectionState state;
    private final ClientSubLevel subLevel;
    private final Map<SectionState.Key,List<BlockEntity>> blockEntities=new LinkedHashMap<>();
    private boolean closed;
    SablePersistentRenderData(SablePersistentProducer owner,ClientSubLevel subLevel) {
        this.owner=owner;this.subLevel=subLevel;state=new SectionState(owner.scene);resize();
    }
    public void resize() {
        owner.assertThread();var box=subLevel.getPlot().getBoundingBox();
        SectionState.Bounds bounds=box==null||box.volume()<=0?null:new SectionState.Bounds(box.minX()>>4,box.minY()>>4,box.minZ()>>4,box.maxX()>>4,box.maxY()>>4,box.maxZ()>>4);
        state.resize(bounds);blockEntities.keySet().removeIf(k->bounds==null||!bounds.contains(k));
    }
    @Override public void close() {owner.assertThread();if(closed)return;closed=true;state.close();blockEntities.clear();owner.removed(this);}
    @Override public void rebuild() {owner.assertThread();state.rebuild();}
    @Override public boolean isSectionCompiled(int x,int y,int z) {return state.compiled(new SectionState.Key(x,y,z));}
    @Override public void setDirty(int x,int y,int z,boolean playerChanged) {owner.assertThread();state.dirty(new SectionState.Key(x,y,z),playerChanged);}
    @Override public void compileSections(PrioritizeChunkUpdates updates,RenderRegionCache regions,Camera camera) {owner.assertThread();compileOne(regions);}
    boolean compileOne(RenderRegionCache regions) {return state.compileOne(key->owner.compile(this,key,regions));}
    @Override public int getVisibleSectionCount() {return state.entries().size();}
    @Override public ClientSubLevel getSubLevel() {return subLevel;}
    void blockEntities(SectionState.Key key,List<BlockEntity> entities) {blockEntities.put(key,List.copyOf(entities));}
    void resetEpoch() {state.ensureEpoch();blockEntities.clear();}
    /** Retained for a future explicit Radiance block-entity stage; this producer does not draw these entities. */
    public List<BlockEntity> blockEntities() {return blockEntities.values().stream().flatMap(List::stream).distinct().toList();}
    public Map<SectionState.Key,String> errors() {return state.errors();}
    public List<SectionState.Entry> sections() {return state.entries();}
}
