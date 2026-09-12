import com.radiance.compat.sable.SablePoseSnapshot;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import org.joml.Matrix4d;
import java.util.Random;
import com.radiance.compat.sable.SableInstancePacket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SablePoseSnapshotTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Random random = new Random(817203);
        int points = 0;
        for (int i = 0; i < 500; i++) {
            Pose3d pose = new Pose3d(new Vector3d(30_000_000 + random.nextDouble(), 85.125, -29_000_000.25),
                new Quaterniond().rotationXYZ(random.nextDouble()*6,random.nextDouble()*6,random.nextDouble()*6),
                new Vector3d(2_000_000.5, 12.25, -1_000_000.25),
                new Vector3d((i%2==0?-1:1)*(0.2+random.nextDouble()*3),0.2+random.nextDouble()*3,0.2+random.nextDouble()*3));
            Vector3d origin = new Vector3d(pose.rotationPoint()).add(16,32,-16);
            SablePoseSnapshot snapshot = SablePoseSnapshot.capture(pose, origin);
            // Reference is the real installed Sable companion's implementation.
            Matrix4d reference = pose.bakeIntoMatrix(new Matrix4d()).translate(origin);
            for (int k=0;k<8;k++) {
                Vector3d local = new Vector3d(random.nextDouble()*16, random.nextDouble()*16, random.nextDouble()*16);
                Vector3d expected = reference.transformPosition(new Vector3d(local));
                Vector3d actual = snapshot.worldMatrix().transformPosition(new Vector3d(local));
                check(expected.distance(actual)<5e-8, "Installed Sable transform disagreement: "+expected.distance(actual));
                points++;
            }
            Vector3d tangent = new Vector3d(1,0,0);
            snapshot.worldMatrix().transformDirection(tangent);
            Vector3d normal = snapshot.normalMatrix().transform(new Vector3d(0,1,0));
            check(Math.abs(normal.dot(tangent))<1e-10,"Normal no longer perpendicular after nonuniform scale");
            Vector3d camera = new Vector3d(pose.position()).add(0.0625,0,-0.03125);
            float[] relative = snapshot.cameraRelative3x4(camera);
            check(Math.abs(relative[3]-(snapshot.rowMajor3x4()[3]-camera.x))<1e-4,"Lost far-origin sub-block translation");
            double before = snapshot.rowMajor3x4()[3];
            pose.position().add(800,0,0); pose.scale().zero();
            double[] exposed = snapshot.rowMajor3x4(); exposed[3] = 0;
            check(snapshot.rowMajor3x4()[3]==before,"Snapshot aliases mutable pose/array");
            boolean rejected = false;
            try { SablePoseSnapshot.capture(pose,origin); } catch (IllegalArgumentException expected) { rejected=true; }
            check(rejected,"Singular scale accepted");
        }
        Pose3d wirePose = new Pose3d(new Vector3d(30_000_000.125,85,-29_000_000.25),
            new Quaterniond(),new Vector3d(128,64,-256),new Vector3d(2,3,-4));
        SablePoseSnapshot wireSnapshot = SablePoseSnapshot.capture(wirePose,new Vector3d(144,64,-256));
        var item = new SableInstancePacket.Instance(41,17,2,wireSnapshot);
        ByteBuffer wire = SableInstancePacket.encode(List.of(item));
        check(wire.isDirect() && wire.remaining()==100 && wire.getInt(0)==0x31495052,"Invalid native packet header");
        check(wire.getLong(16)==41 && wire.getLong(24)==17 && wire.getLong(32)==2,"Invalid IDs");
        check(wire.getFloat(40)==2 && wire.getFloat(56)==3 && wire.getFloat(72)==-4,"Invalid linear matrix offsets");
        check(wire.getDouble(76)==30_000_032.125 && wire.getDouble(84)==85 && wire.getDouble(92)==-29_000_000.25,"Invalid unaligned double offsets");
        check(SableInstancePacket.encode(List.of()).remaining()==16,"Empty visibility packet malformed");
        boolean duplicateRejected=false;
        try { SableInstancePacket.encode(List.of(item,item)); } catch (IllegalArgumentException expected) { duplicateRejected=true; }
        check(duplicateRejected,"Duplicate instance ID accepted");
        if(args.length>0) {
            byte[] fixture=new byte[wire.remaining()]; wire.get(fixture); Files.write(Path.of(args[0]),fixture);
        }
        System.out.println("PASS: "+points+" installed-Sable reference positions; 500 normal, precision, snapshot and singular-pose checks; RPI1 layout/IDs/empty/duplicate checks");
    }
}
