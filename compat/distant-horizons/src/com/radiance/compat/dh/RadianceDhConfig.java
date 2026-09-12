package com.radiance.compat.dh;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Source geometry budgets, not a VRAM promise: expanded PBR/BLAS memory is larger. */
public record RadianceDhConfig(long selectedBytes, long liveBytes, long queueBytes) {
    public static volatile RadianceDhConfig current = new RadianceDhConfig(0, 0, 32L << 20);
    public RadianceDhConfig {
        if (selectedBytes < 0 || liveBytes < 0 || queueBytes < 0)
            throw new IllegalArgumentException("DH budgets must be nonnegative; 0 means unlimited");
        if (liveBytes != 0 && (selectedBytes == 0 || selectedBytes > liveBytes / 2))
            throw new IllegalArgumentException("Finite DH live budget requires selected budget <= half live budget for replacement");
        if (queueBytes != 0 && queueBytes < (2L << 20) + 80)
            throw new IllegalArgumentException("DH queue must fit a maximum packet (use at least 3 MiB, or 0)");
    }
    public static RadianceDhConfig load(Path file) {
        Properties p = new Properties();
        try {
            if (Files.exists(file)) try (Reader in = Files.newBufferedReader(file)) { p.load(in); }
            else {
                p.setProperty("selectedSourceMiB", "0"); p.setProperty("liveSourceMiB", "0");
                p.setProperty("queueMiB", "32"); Files.createDirectories(file.getParent());
                try (Writer out = Files.newBufferedWriter(file)) {
                    p.store(out, "Radiance DH: 0 removes the source memory cap. Queue only controls upload backpressure. Restart to apply.");
                }
            }
            return new RadianceDhConfig(mib(p, "selectedSourceMiB", "0"), mib(p, "liveSourceMiB", "0"), mib(p, "queueMiB", "32"));
        } catch (IOException | NumberFormatException | ArithmeticException e) {
            throw new IllegalStateException("Invalid Radiance DH configuration: " + file, e);
        }
    }
    private static long mib(Properties p, String key, String fallback) {
        return Math.multiplyExact(Long.parseLong(p.getProperty(key, fallback).trim()), 1L << 20);
    }
}
