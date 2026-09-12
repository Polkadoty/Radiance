package com.radiance.compat.sable.producer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Epoch-scoped native ownership, shared by all sublevels in one producer scene. */
public final class SceneMeshes {
    public interface Sink {
        long reset();
        void material(long epoch,long id,PbrSectionMeshes.Material material);
        void mesh(long epoch,long id,long materialId,ByteBuffer packet);
        void retireMesh(long epoch,long id);
        void retireMaterial(long epoch,long id);
        void instances(long epoch,ByteBuffer packet);
    }
    public record Handle(long meshId,long materialId) {}
    private final Sink sink;
    private final Map<PbrSectionMeshes.Material,Long> materialIds=new HashMap<>();
    private long epoch,nextMesh=1,nextMaterial=1;
    public SceneMeshes(Sink sink) { this.sink=sink; epoch=sink.reset(); }
    public long epoch() { return epoch; }
    public void reset() { epoch=sink.reset();nextMesh=1;nextMaterial=1;materialIds.clear(); }
    public List<Handle> upload(PbrSectionMeshes.Result result) {
        if(nextMesh+result.meshes().size()>65537)throw new IllegalStateException("Persistent mesh epoch ID capacity reached; reload the producer scene");
        List<Handle> accepted=new ArrayList<>();
        try {
            for(var mesh:result.meshes()) {
                Long material=materialIds.get(mesh.material());
                if(material==null) {
                    if(nextMaterial>65536)throw new IllegalStateException("Persistent material capacity reached");
                    material=nextMaterial++; sink.material(epoch,material,mesh.material()); materialIds.put(mesh.material(),material);
                }
                long id=nextMesh++; sink.mesh(epoch,id,material,mesh.packet()); accepted.add(new Handle(id,material));
            }
            return List.copyOf(accepted);
        } catch(RuntimeException error) {
            for(Handle h:accepted)try {sink.retireMesh(epoch,h.meshId());}catch(RuntimeException suppressed){error.addSuppressed(suppressed);}
            throw error;
        }
    }
    public void retire(long ownerEpoch,List<Handle> handles) {
        if(ownerEpoch!=epoch)return; // Old native scene already retired at reset.
        for(Handle h:handles)sink.retireMesh(epoch,h.meshId());
    }
    public void submit(ByteBuffer packet) {sink.instances(epoch,packet);}
}
