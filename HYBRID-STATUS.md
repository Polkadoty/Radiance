# Hybrid Vulkan 1.21.1 development checkpoint

Use `feature/hybrid-vulkan-1.21.1` in both Polkadoty/Radiance and Polkadoty/MCVR for continued hybrid development. The frame-generation and create-bridge branches are earlier component checkpoints. This is a development branch, not an end-user release.

## Current scope

- Minecraft 1.21.1, NeoForge 21.1.249; Windows hybrid backend.
- Radiance `0.1.5-alpha-port.1-hybrid.8`, hybrid companion `0.0.2-native-hud.6`.
- Native Vulkan world renderer, DLSS integration, experimental frame generation, DH and persistent Sable geometry bridges, and the integrated cloud/ocean shaderpack.
- Standard HUD shaders now use native Vulkan, including text, bars, icons and item/glint draws. Ordered compatibility segments preserve unsupported HUD draws, with at most two snapshots per HUD frame. Xaero's full-screen map and private framebuffer consumers retain OpenGL compatibility rendering. Its minimap sampling draw also remains in GL because that image is produced in a private GL framebuffer.
- NeoForge model data and declared cutout layers are respected by terrain compilation; chest stencil-state and shared-resource shutdown fixes are retained.
- Flywheel is disabled in the tested pack. Zink is not installed.

`compat/hybrid`, `compat/distant-horizons` and `compat/sable` contain the companion sources and metadata. `shaderpacks/Square-Skies-DH-Integration-0.1.1` contains the exact pack from the test instance, without its per-user option sidecar. Shaderpack license and provenance are included.

The Sable sources include the producer and callback modules required by its mixin configs. `compat/veil-overlay` preserves the modified Veil boundary source files; it must be applied to the matching Veil source version, and is not a standalone mod. The shaderpack differs from the installed source only by trailing-whitespace cleanup in one cloud shader.

## Next work, in order

1. Validate Hybrid8/native-hud.6 after the CTM decoder and custom-layout startup fixes, then record comparable CPU/GPU frame times with `radiance.nativeHud=true` and `false`. The user confirmed the matching .3 native HUD/map-crash fix visually. Performance gain is not established.
2. Validate explicit vertex-layout registration across resource reload/resize, then implement native offscreen color/depth/stencil targets with correct resize, sampling, texture identity and state restoration. These are shared prerequisites for minimaps and mod UI/postprocessing.
3. Remove the remaining custom-layout/private-target HUD fallback after native target support is available. Preserve ordering, stencil and alpha semantics; do not route arbitrary GL calls into Vulkan without an equivalent target/state implementation.
4. Validate additional-section geometry visually, extend capture to the Sable producer, and audit custom world and particle producers for material, transform and visibility submission into the ray-traced scene.
5. Integrate and validate Flywheel instancing/compute and moving Create contraptions; retain a compatibility fallback for unsupported producers.
6. Validate the complete pack: server joins, chest/UI transitions, resource reloads, dimension changes, long-distance DH travel, water, and DLSS SR/RR with frame generation toggled. Package reproducible builds and document supported combinations.

## Validation and build limits

Hybrid7 adds client-thread collection and worker execution of NeoForge additional-section geometry, including otherwise empty sections. Three runtime probes passed in the full modpack: region capture, worker execution, identity pose and PBR solid/cutout/translucent buffers. The probes emit no vertices, so this is not visual validation of every mod's geometry. The Sable four-argument compiler entry still uses the empty callback set.

Hybrid7 also includes a scoped Indigo material-layer adapter for Continuity overlays and allows upload of auxiliary maps from `optifine/ctm` paths. Hybrid8 corrects the missing CTM directory scan in the decoded material cache; see `docs/hybrid/HYBRID8-VALIDATION.md`. The connected-texture fixes passed the NeoForge build and native-resource verification, and the user confirmed the installed fix worked. Existing Aeronautics PBR textures already include the reported Stay True grass overlays; no texture assets were changed. Leaf shelf brightness remains under investigation; sampled oak/birch maps have emission disabled.

The required copy of `core.lib` was removed: it is a build-time Windows import library and is not needed to load `core.dll`. Hybrid7 native binaries were unchanged from Hybrid6. Hybrid8 requires its matching rebuilt MCVR core for explicit overlay layouts. `tools/stage-hybrid-test.py` checks the installed DLL identity and preserves companion bytecode while updating exact dependency metadata. The companion Java sources are still not built by the main Gradle project.

See `docs/hybrid/HYBRID7-VALIDATION.md` and `docs/hybrid/NATIVE-HUD-VALIDATION.md` for the tested/staged distinction. `tools/build-hybrid-companion.py` builds the companion from source against a matching main JAR and cached NeoForge dependencies.

The originating Hybrid6 NeoForge build and ten GPU startup checks passed. The local regression world loaded and shutdown completed cleanly. Corrected model-layer routing was logged for three affected bush models. Visual confirmation of foliage and migrated hotbar items remains pending. Active frame generation and comparative performance were not validated in that run.

This consolidation verifies source equivalence; it has not repeated the full runtime test from a clean clone. The standard build still needs native dependencies, generated JNI headers, shader resources and vendor runtimes. Those DLLs, local test worlds, accounts and launcher configs are not included. Companion build automation and a clean-clone unified packaging task remain work to finish before release. Use the existing Hybrid Foundation Test Prism instance for the installed test build.
