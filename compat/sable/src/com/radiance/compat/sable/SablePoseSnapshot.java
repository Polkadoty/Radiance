package com.radiance.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Immutable mesh-local to world transform, captured from the interpolated render pose. */
public final class SablePoseSnapshot {
    private final double[] rows;

    private SablePoseSnapshot(double[] rows) {
        this.rows = rows;
    }

    /** Call once on the render thread with sublevel.renderPose(partialTick). */
    public static SablePoseSnapshot capture(Pose3dc pose, Vector3dc meshOrigin) {
        // A render pose is mutable/reused by Sable: copy every value now.
        Matrix3d linear = new Matrix3d().rotation(pose.orientation()).scale(pose.scale());
        Vector3d translation = new Vector3d(meshOrigin).sub(pose.rotationPoint());
        linear.transform(translation).add(pose.position());
        double[] rows = {
            linear.m00(), linear.m10(), linear.m20(), translation.x,
            linear.m01(), linear.m11(), linear.m21(), translation.y,
            linear.m02(), linear.m12(), linear.m22(), translation.z
        };
        for (double value : rows) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite Sable pose");
        }
        double determinant = linear.determinant();
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12) {
            throw new IllegalArgumentException("Singular Sable pose cannot define ray-traced normals");
        }
        return new SablePoseSnapshot(rows);
    }

    /** Three row-major affine rows. Translation remains double precision. */
    public double[] rowMajor3x4() {
        return rows.clone();
    }

    /** Native scene API stores this separately from its high-precision translation. */
    public float[] linearRowMajor3x3() {
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float value = (float) rows[row * 4 + col];
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Sable linear transform exceeds float range");
                result[row * 3 + col] = value;
            }
        }
        return result;
    }

    public double[] worldTranslation() {
        return new double[] { rows[3], rows[7], rows[11] };
    }

    void writeNativeAffine(ByteBuffer destination) {
        if (destination.order() != ByteOrder.LITTLE_ENDIAN || destination.remaining() < 60) {
            throw new IllegalArgumentException("Affine requires 60 bytes in a little-endian packet");
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float value = (float) rows[row * 4 + col];
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Sable linear transform exceeds float range");
                destination.putFloat(value);
            }
        }
        destination.putDouble(rows[3]).putDouble(rows[7]).putDouble(rows[11]);
    }

    public Matrix4d worldMatrix() {
        return new Matrix4d(
            rows[0], rows[4], rows[8], 0,
            rows[1], rows[5], rows[9], 0,
            rows[2], rows[6], rows[10], 0,
            rows[3], rows[7], rows[11], 1);
    }

    /** Convert after subtracting the camera in doubles, never before. */
    public float[] cameraRelative3x4(Vector3dc camera) {
        float[] result = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) result[row * 4 + col] = (float) rows[row * 4 + col];
            result[row * 4 + 3] = (float) (rows[row * 4 + 3] - camera.get(row));
        }
        for (float value : result) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid camera-relative Sable pose");
        }
        return result;
    }

    /** Nonuniform and reflected scale require inverse-transpose, not rotation alone. */
    public Matrix3d normalMatrix() {
        return new Matrix3d(worldMatrix()).invert().transpose();
    }
}
