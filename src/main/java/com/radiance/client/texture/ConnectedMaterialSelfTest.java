package com.radiance.client.texture;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Exercises resource discovery, PNG decoding and CTM cache keys without replacing the live cache. */
final class ConnectedMaterialSelfTest {
    static void run() {
        Map<Identifier, Resource> files = new HashMap<>();
        Identifier base = Identifier.of("radiance_test", "optifine/ctm/path/7.png");
        int[] pixels = {0xff315a91, 0xffea807f, 0xff070605};
        String[] suffixes = {"_s", "_n", "_f"};
        try {
            for (int i = 0; i < suffixes.length; i++) {
                byte[] png;
                try (NativeImage image = new NativeImage(2, 2, false)) {
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) image.setColor(x, y, pixels[i]);
                    png = image.getBytes();
                }
                files.put(base.withPath(base.getPath().replace(".png", suffixes[i] + ".png")),
                    new Resource(null, () -> new ByteArrayInputStream(png)));
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("CTM test PNG encoding", e); }
        // Use typed overrides so Loom remaps the methods along with the production call.
        ResourceManager manager = new ResourceManager() {
            @Override public Map<Identifier, Resource> findResources(String prefix, Predicate<Identifier> filter) {
                Map<Identifier, Resource> found = new HashMap<>();
                files.forEach((id, resource) -> {
                    if (id.getPath().startsWith(prefix + "/") && filter.test(id)) found.put(id, resource);
                });
                return found;
            }
            @Override public java.util.Set<String> getAllNamespaces() { return java.util.Set.of("radiance_test"); }
            @Override public java.util.Optional<Resource> getResource(Identifier id) { return java.util.Optional.ofNullable(files.get(id)); }
            @Override public java.util.List<Resource> getAllResources(Identifier id) { return getResource(id).stream().toList(); }
            @Override public Map<Identifier, java.util.List<Resource>> findAllResources(String prefix, Predicate<Identifier> filter) {
                Map<Identifier, java.util.List<Resource>> result = new HashMap<>();
                findResources(prefix, filter).forEach((id, resource) -> result.put(id, java.util.List.of(resource)));
                return result;
            }
            @Override public java.util.stream.Stream<net.minecraft.resource.ResourcePack> streamResourcePacks() { return java.util.stream.Stream.empty(); }
        };
        var prepared = AuxiliaryTextures.prepareDecodedImagesAsync(manager, Runnable::run).join();
        try {
            var types = AuxiliaryTextures.values();
            for (int i = 0; i < types.length; i++) {
                NativeImage actual = prepared.image(types[i], base, 0);
                if (actual == null || actual.getColor(0, 0) != pixels[i] || actual.getColor(1, 1) != pixels[i])
                    throw new IllegalStateException("CTM material missing or wrong pixels: " + types[i]);
                NativeImage mip = prepared.image(types[i], base, 1);
                if (mip == null || mip.getWidth() != 1 || mip.getHeight() != 1)
                    throw new IllegalStateException("CTM material mip missing: " + types[i]);
            }
            if (prepared.image(AuxiliaryTextures.NORMAL, base.withPath("optifine/ctm/path/missing.png"), 0) != null)
                throw new IllegalStateException("Absent CTM material must retain the neutral fallback");
        } finally { prepared.close(); }
        System.out.println("[Radiance] CTM material resource scan/normal/specular/flag PNG pixels/mips/missing map PASS");
    }
}
