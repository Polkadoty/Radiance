import com.radiance.compat.sable.producer.PbrSectionMeshes;
import com.radiance.compat.sable.producer.SceneMeshes;
import com.radiance.compat.sable.producer.SectionState;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProducerTest {
    private ProducerTest() {}
    private static ByteBuffer quad(int texture,int alpha,boolean normals) {
        ByteBuffer b=ByteBuffer.allocate(4*128).order(ByteOrder.LITTLE_ENDIAN);
        for(int v=0;v<4;v++) {
            int p=v*128;b.putFloat(p,v==1||v==2?1:0).putFloat(p+4,v>=2?1:0).putFloat(p+8,0);
            b.putInt(p+12,normals?1:0);b.putFloat(p+24,normals?2:0);b.putInt(p+28,1);
            b.putFloat(p+32,.25f).putFloat(p+36,.5f).putFloat(p+40,.75f).putFloat(p+44,1);
            b.putInt(p+48,1).putInt(p+52,1).putInt(p+68,10); // neutral vanilla overlay
            b.putFloat(p+56,v==1||v==2?.375f:.25f).putFloat(p+60,v>=2?.625f:.5f);
            b.putInt(p+76,texture).putInt(p+92,1).putInt(p+96,240).putInt(p+100,240).putFloat(p+108,.125f).putInt(p+124,alpha);
        }
        return b;
    }
    private static ByteBuffer together(ByteBuffer... sources) {
        int size=0;for(var b:sources)size+=b.remaining();ByteBuffer out=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        for(var b:sources)out.put(b.duplicate());return out.flip();
    }
    private static void rejects(Runnable call) {try{call.run();throw new AssertionError("expected input rejection");}catch(IllegalArgumentException expected){}}
    private static final class Sink implements SceneMeshes.Sink {
        long epoch;int uploads,resets,retired,failAt=Integer.MAX_VALUE;Set<Long> meshes=new HashSet<>();List<Long> meshIds=new ArrayList<>();
        @Override public long reset(){meshes.clear();resets++;return ++epoch;}
        @Override public void material(long e,long id,PbrSectionMeshes.Material m){assert e==epoch;assert m.alpha()<2;}
        @Override public void mesh(long e,long id,long material,ByteBuffer packet){assert e==epoch;if(++uploads==failAt)throw new IllegalStateException("injected upload failure");assert packet.isDirect()&&packet.getInt(0)==0x314d5052;assert meshes.add(id);meshIds.add(id);}
        @Override public void retireMesh(long e,long id){assert e==epoch;assert meshes.remove(id);retired++;}
        @Override public void retireMaterial(long e,long id){assert e==epoch;}
        @Override public void instances(long e,ByteBuffer packet){assert e==epoch;}
    }
    public static void main(String[] args) throws Exception {
        ByteBuffer original=together(quad(17,0,true),quad(99,1,false),quad(17,0,true),quad(17,2,true));
        var converted=PbrSectionMeshes.copy(original,16,128);assert converted.meshes().size()==2;assert converted.unsupportedQuads().get("transparent_or_custom_alpha")==1;
        var opaque=converted.meshes().get(0);assert opaque.material().texture()==17&&opaque.material().alpha()==0&&opaque.quads()==2;
        ByteBuffer packet=opaque.packet();assert packet.getInt(8)==8&&packet.getInt(12)==12&&packet.getFloat(16+20)==1;
        assert packet.getFloat(16+32)==.25f&&packet.getFloat(16+24)==.25f; // tint and UV offsets in RPM1
        assert packet.getInt(16+8*48+6*4)==4; // second grouped quad indices rebased
        assert converted.meshes().get(1).packet().getFloat(16+20)==1; // generated unit normal
        for(int i=0;i<original.capacity();i++)original.put(i,(byte)0);
        assert opaque.packet().getFloat(16+32)==.25f; // survives original MeshData buffer destruction/reuse
        packet.position(packet.limit());assert opaque.packet().position()==0;
        rejects(()->PbrSectionMeshes.copy(quad(1,0,true),4,32));
        rejects(()->PbrSectionMeshes.copy(quad(1,0,true),3,128));
        var malformed=quad(1,0,true);malformed.putFloat(0,Float.NaN);rejects(()->PbrSectionMeshes.copy(malformed,4,128));
        var mixed=quad(1,0,true);mixed.putInt(128+76,2);assert PbrSectionMeshes.copy(mixed,4,128).unsupportedQuads().get("per_vertex_material")==1;
        var overlay=quad(1,0,true);overlay.putInt(64,2);assert PbrSectionMeshes.copy(overlay,4,128).unsupportedQuads().get("nonneutral_overlay")==1;
        var glint=quad(1,0,true);glint.putInt(72,1);assert PbrSectionMeshes.copy(glint,4,128).unsupportedQuads().get("glint")==1;
        Sink sink=new Sink();SceneMeshes scene=new SceneMeshes(sink);SectionState state=new SectionState(scene);
        var low=new SectionState.Key(-1,0,0);var high=new SectionState.Key(0,0,0);
        state.resize(new SectionState.Bounds(-1,0,0,0,0,0));assert state.dirtyCount()==2;
        state.dirty(high,true);List<SectionState.Key> order=new ArrayList<>();
        var one=PbrSectionMeshes.copy(quad(17,0,true),4,128);
        assert state.compileOne(k->{order.add(k);return one;});assert order.get(0).equals(high);assert sink.uploads==1&&state.dirtyCount()==1;
        assert state.compileOne(k->{order.add(k);return one;});assert state.compiled(low)&&state.compiled(high);
        int stableUploads=sink.uploads;state.entries();state.entries();assert sink.uploads==stableUploads; // transform-only access does not rebuild meshes
        state.dirty(high,false);state.compileOne(k->{throw new IllegalArgumentException("bad geometry");});
        assert state.errors().size()==1&&state.entries().size()==2&&sink.meshes.size()==2&&!state.compiled(high);
        state.dirty(high,false);state.compileOne(k->one);assert sink.retired==1&&state.errors().isEmpty();
        state.resize(new SectionState.Bounds(0,0,0,0,0,0));assert sink.retired==2&&state.entries().size()==1;
        rejects(()->state.resize(new SectionState.Bounds(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,0,0)));assert state.entries().size()==1;
        long oldEpoch=scene.epoch();scene.reset();state.ensureEpoch();assert scene.epoch()!=oldEpoch&&state.entries().isEmpty()&&state.dirtyCount()==1&&sink.meshes.isEmpty();
        state.compileOne(k->one);int beforeClose=sink.retired;state.close();state.close();assert sink.retired==beforeClose+1;
        // Fresh IDs make a multi-material upload failure atomic for the old visible section.
        SectionState failure=new SectionState(scene);failure.resize(new SectionState.Bounds(0,0,0,0,0,0));failure.compileOne(k->one);
        Set<Long> oldMeshes=Set.copyOf(sink.meshes);sink.failAt=sink.uploads+2;failure.dirty(high,false);
        failure.compileOne(k->converted);assert sink.meshes.equals(oldMeshes)&&failure.errors().size()==1;
        failure.close();assert sink.meshes.isEmpty();
        byte[] bytes=new byte[opaque.packet().remaining()];opaque.packet().get(bytes);Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[0],"pbr-section-rpm1.bin"),bytes);
        System.out.println("PASS: actual PBR128 offsets, partition/tint/UV/normals/index conversion, source lifetime, unsupported surfaces, dirty priority/bounds/epoch/retirement and transactional upload failure");
    }
}
