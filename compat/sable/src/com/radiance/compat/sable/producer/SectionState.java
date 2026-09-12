package com.radiance.compat.sable.producer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** CPU lifecycle and dirty scheduling. All public operations run on the render thread. */
public final class SectionState {
    public record Key(int x,int y,int z) {}
    public record Bounds(int x0,int y0,int z0,int x1,int y1,int z1) {
        boolean contains(Key k) {return k.x>=x0&&k.x<=x1&&k.y>=y0&&k.y<=y1&&k.z>=z0&&k.z<=z1;}
        long count() {long x=(long)x1-x0+1,y=(long)y1-y0+1,z=(long)z1-z0+1;return x<1||y<1||z<1||x>4096||y>4096||z>4096?Long.MAX_VALUE:x*y*z;}
    }
    public record Entry(Key key,long epoch,List<SceneMeshes.Handle> meshes,Map<String,Integer> unsupported) {}
    private final SceneMeshes scene;
    private final Map<Key,Entry> entries=new LinkedHashMap<>();
    private final Map<Key,Boolean> dirty=new LinkedHashMap<>();
    private final Map<Key,String> errors=new LinkedHashMap<>();
    private Bounds bounds;
    private long epoch;
    private boolean closed;
    public SectionState(SceneMeshes scene) {this.scene=scene;epoch=scene.epoch();}
    public void resize(Bounds next) {
        if(closed)throw new IllegalStateException("Closed Sable render data");
        if(next!=null && (next.x1<next.x0 || next.y1<next.y0 || next.z1<next.z0 || next.count()>4096 || next.count()<1))
            throw new IllegalArgumentException("Sublevel exceeds bounded4096-section producer grid");
        boolean changed=!java.util.Objects.equals(bounds,next);bounds=next;
        for(var it=entries.entrySet().iterator();it.hasNext();) {var e=it.next();if(next==null||!next.contains(e.getKey())) {scene.retire(e.getValue().epoch,e.getValue().meshes);it.remove();}}
        dirty.keySet().removeIf(k->next==null||!next.contains(k)); errors.keySet().removeIf(k->next==null||!next.contains(k));
        if(next!=null)for(long x=next.x0;x<=next.x1;x++)for(long y=next.y0;y<=next.y1;y++)for(long z=next.z0;z<=next.z1;z++) {
            Key k=new Key((int)x,(int)y,(int)z);if(!entries.containsKey(k))dirty.putIfAbsent(k,false);
        }
        if(changed)rebuild(); // Changes at a plot boundary can change adjacent face culling.
    }
    public void dirty(Key key,boolean important) {if(!closed&&bounds!=null&&bounds.contains(key)){dirty.merge(key,important,(a,b)->a||b);errors.remove(key);}}
    public void rebuild() {if(bounds!=null)for(long x=bounds.x0;x<=bounds.x1;x++)for(long y=bounds.y0;y<=bounds.y1;y++)for(long z=bounds.z0;z<=bounds.z1;z++)dirty(new Key((int)x,(int)y,(int)z),false);}
    public void ensureEpoch() {
        if(epoch!=scene.epoch()) {entries.clear();errors.clear();dirty.clear();epoch=scene.epoch();rebuild();}
    }
    /** One non-preemptible section compile per call; caller owns the total per-frame budget. */
    public boolean compileOne(Function<Key,PbrSectionMeshes.Result> compiler) {
        ensureEpoch();if(closed||dirty.isEmpty())return false;
        Key next=dirty.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).findFirst().orElse(dirty.keySet().iterator().next());
        dirty.remove(next);
        try {
            PbrSectionMeshes.Result result=compiler.apply(next);
            List<SceneMeshes.Handle> uploaded=scene.upload(result);
            Entry old=entries.put(next,new Entry(next,epoch,uploaded,result.unsupportedQuads()));
            if(old!=null)scene.retire(old.epoch,old.meshes);
            errors.remove(next);
        } catch(RuntimeException e) {errors.put(next,e.getClass().getSimpleName()+": "+e.getMessage());}
        return true;
    }
    public boolean compiled(Key k) {ensureEpoch();return entries.containsKey(k)&&!dirty.containsKey(k)&&!errors.containsKey(k);}
    public List<Entry> entries() {ensureEpoch();return List.copyOf(entries.values());}
    public Map<Key,String> errors() {return Map.copyOf(errors);}
    public int dirtyCount() {return dirty.size();}
    public void close() {if(closed)return;for(Entry e:entries.values())scene.retire(e.epoch,e.meshes);entries.clear();dirty.clear();errors.clear();closed=true;}
}
