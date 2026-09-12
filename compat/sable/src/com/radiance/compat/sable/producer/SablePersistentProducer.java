package com.radiance.compat.sable.producer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.proxy.world.PersistentSceneProxy;
import com.radiance.client.vertex.PBRVertexFormats;
import com.radiance.compat.sable.SableInstancePacket;
import com.radiance.compat.sable.SablePoseSnapshot;
import com.radiance.compat.sable.producer.mixin.ModelCacheAccess;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.joml.Vector3d;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicitly enabled Vulkan geometry producer; parent compatibility module owns dispatcher/frame hooks. */
public final class SablePersistentProducer implements AutoCloseable {
    public static boolean enabled() {return Boolean.getBoolean("radiance.sableBridge");}
    final SceneMeshes scene;
    private final Map<ClientSubLevel,SablePersistentRenderData> data=new LinkedHashMap<>();
    private final SectionBufferBuilderPack buffers;
    private final SectionCompiler compiler;
    private int cursor;
    private boolean closed;
    public SablePersistentProducer() {
        if(!enabled())throw new IllegalStateException("Sable persistent producer is disabled; set radiance.sableBridge=true for the isolated test");
        assertThread();var mc=Minecraft.getInstance();
        scene=new SceneMeshes(new NativeSink());
        buffers=new SectionBufferBuilderPack();compiler=new SectionCompiler(mc.getBlockRenderer(),mc.getBlockEntityRenderDispatcher());
    }
    void assertThread() {RenderSystem.assertOnRenderThread();if(closed)throw new IllegalStateException("Closed Sable persistent producer");}
    public SablePersistentRenderData createRenderData(ClientSubLevel subLevel) {
        assertThread();var old=data.get(subLevel);if(old!=null)old.close();
        var created=new SablePersistentRenderData(this,subLevel);data.put(subLevel,created);return created;
    }
    void removed(SablePersistentRenderData value) {data.remove(value.getSubLevel(),value);}
    PbrSectionMeshes.Result compile(SablePersistentRenderData target,SectionState.Key key,RenderRegionCache regions) {
        assertThread();var section=SectionPos.of(key.x(),key.y(),key.z());
        var region=regions.createRegion(target.getSubLevel().getLevel(),section);
        if(region==null) {target.blockEntities(key,List.of());return new PbrSectionMeshes.Result(List.of(),Map.of());}
        var cache=(ModelBlockRendererCacheExtension)ModelCacheAccess.radianceSableCache().get();
        boolean oldOnSubLevel=cache.sable$getOnSubLevel();cache.sable$setOnSubLevel(true);
        SectionCompiler.Results result=null;
        try {
            // This exact four-argument path is intercepted by Radiance SectionBuilderMixins.
            result=compiler.compile(section,region,VertexSorting.DISTANCE_TO_ORIGIN,buffers);
            List<PbrSectionMeshes.Mesh> meshes=new ArrayList<>();Map<String,Integer> unsupported=new LinkedHashMap<>();
            for(MeshData mesh:result.renderedLayers.values()) {
                var draw=mesh.drawState();
                if(draw.format()!=PBRVertexFormats.PBR_TRIANGLE || draw.format().getVertexSize()!=128 || draw.mode()!=VertexFormat.Mode.QUADS)
                    throw new IllegalStateException("SectionCompiler did not return Radiance PBR128 QUADS; incompatible compiler/mixin path");
                if(draw.indexCount()!=draw.vertexCount()/4*6)throw new IllegalStateException("Unexpected section index topology");
                // Deep copy before Results.release()/buffer clear invalidates native MeshData storage.
                var copied=PbrSectionMeshes.copy(mesh.vertexBuffer(),draw.vertexCount(),draw.format().getVertexSize());
                meshes.addAll(copied.meshes());copied.unsupportedQuads().forEach((reason,count)->unsupported.merge(reason,count,Integer::sum));
            }
            List<net.minecraft.world.level.block.entity.BlockEntity> entities=new ArrayList<>(result.blockEntities);entities.addAll(result.globalBlockEntities);
            target.blockEntities(key,entities);
            return new PbrSectionMeshes.Result(meshes,unsupported);
        } finally {
            try {if(result!=null)result.release();}
            finally {cache.sable$setOnSubLevel(oldOnSubLevel);ModelBlockRenderer.clearCache();buffers.clearAll();}
        }
    }
    /** Call once inside Radiance's own world frame, before native submit; not a vanilla render-stage listener. */
    public void frame(float partialTick) {
        assertThread();List<SablePersistentRenderData> snapshot=List.copyOf(data.values());
        // One bounded work item, round-robin across sublevels. The section itself is non-preemptible.
        for(int checked=0;checked<snapshot.size();checked++) {
            int index=Math.floorMod(cursor++,snapshot.size());
            if(snapshot.get(index).compileOne(new RenderRegionCache()))break;
        }
        List<SableInstancePacket.Instance> instances=new ArrayList<>();
        for(var sublevel:snapshot) {
            var pose=sublevel.getSubLevel().renderPose(partialTick);
            for(var section:sublevel.sections()) {
                var origin=new Vector3d((double)section.key().x()*16,(double)section.key().y()*16,(double)section.key().z()*16);
                var affine=SablePoseSnapshot.capture(pose,origin);
                for(var mesh:section.meshes())instances.add(new SableInstancePacket.Instance(mesh.meshId(),mesh.meshId(),1,affine));
            }
        }
        scene.submit(SableInstancePacket.encode(instances));
    }
    /** After resource/atlas reload. World switch should close this producer and construct a new one. */
    public void resetResources() {assertThread();scene.reset();for(var d:data.values())d.resetEpoch();}
    public Map<String,Integer> diagnostics() {
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(var d:data.values()) {counts.merge("dirty_sections",d.state.dirtyCount(),Integer::sum);counts.merge("failed_sections",d.errors().size(),Integer::sum);counts.merge("pending_block_entities",d.blockEntities().size(),Integer::sum);
            for(var s:d.sections())s.unsupported().forEach((key,value)->counts.merge(key,value,Integer::sum));}
        return Map.copyOf(counts);
    }
    @Override public void close() {if(closed)return;assertThread();for(var d:List.copyOf(data.values()))d.close();scene.reset();buffers.close();closed=true;}
    private static final class NativeSink implements SceneMeshes.Sink {
        @Override public long reset(){return PersistentSceneProxy.reset();}
        @Override public void material(long e,long id,PbrSectionMeshes.Material m){PersistentSceneProxy.material(e,id,1,m.texture(),m.alpha(),m.emission());}
        @Override public void mesh(long e,long id,long material,ByteBuffer packet){PersistentSceneProxy.mesh(e,id,1,material,1,packet,packet.remaining());}
        @Override public void retireMesh(long e,long id){PersistentSceneProxy.retireMesh(e,id);}
        @Override public void retireMaterial(long e,long id){PersistentSceneProxy.retireMaterial(e,id);}
        @Override public void instances(long e,ByteBuffer packet){PersistentSceneProxy.instances(e,packet,packet.remaining());}
    }
}
