# Hybrid Vulkan 1.21.1 development checkpoint

Use `feature/hybrid-vulkan-1.21.1` in both Polkadoty/Radiance and Polkadoty/MCVR for continued hybrid development. The frame-generation and create-bridge branches are earlier component checkpoints. This branch consolidates the Hybrid6 source snapshot, not a new end-user release.

## Current scope

- Minecraft 1.21.1, NeoForge 21.1.249; Windows hybrid backend.
- Radiance `0.1.5-alpha-port.1-hybrid.6`, hybrid companion `0.0.2-native-hud.2`.
- Native Vulkan world renderer, DLSS integration, experimental frame generation, DH and persistent Sable geometry bridges, and the integrated cloud/ocean shaderpack.
- Standard menus and item hotbar use native Vulkan paths. Remaining HUD, minimap, custom layouts and private framebuffer consumers retain OpenGL compatibility rendering.
- NeoForge model data and declared cutout layers are respected by terrain compilation; chest stencil-state and shared-resource shutdown fixes are retained.
- Flywheel is disabled in the tested pack. Zink is not installed.

`compat/hybrid`, `compat/distant-horizons` and `compat/sable` contain the companion sources and metadata. `shaderpacks/Square-Skies-DH-Integration-0.1.1` contains the exact pack from the test instance, without its per-user option sidecar. Shaderpack license and provenance are included.

The Sable sources include the producer and callback modules required by its mixin configs. `compat/veil-overlay` preserves the modified Veil boundary source files; it must be applied to the matching Veil source version, and is not a standalone mod. The shaderpack differs from the installed source only by trailing-whitespace cleanup in one cloud shader.

## Next work, in order

1. Confirm the corrected bushes and hotbar item/glint rendering visually, and record comparable CPU/GPU frame times with the hotbar migration enabled and disabled. The native hotbar currently adds a full-resolution compatibility snapshot; performance gain is not established.
2. Implement general vertex-layout registration and native offscreen color/depth/stencil targets with correct resize, sampling, texture identity and state restoration. These are shared prerequisites for minimaps and mod UI/postprocessing.
3. Migrate the remaining HUD in coherent groups while preserving draw order and alpha blending. Reduce shared-image copies rather than adding a copy at every HUD element.
4. Complete NeoForge additional-section-geometry hooks and audit custom world and particle producers for material, transform and visibility submission into the ray-traced scene.
5. Integrate and validate Flywheel instancing/compute and moving Create contraptions; retain a compatibility fallback for unsupported producers.
6. Validate the complete pack: server joins, chest/UI transitions, resource reloads, dimension changes, long-distance DH travel, water, and DLSS SR/RR with frame generation toggled. Package reproducible builds and document supported combinations.

## Validation and build limits

The originating Hybrid6 NeoForge build and ten GPU startup checks passed. The local regression world loaded and shutdown completed cleanly. Corrected model-layer routing was logged for three affected bush models. Visual confirmation of foliage and migrated hotbar items remains pending. Active frame generation and comparative performance were not validated in that run.

This consolidation verifies source equivalence; it has not repeated the full runtime test from a clean clone. The standard build still needs native dependencies, generated JNI headers, shader resources and vendor runtimes. Those DLLs, local test worlds, accounts and launcher configs are not included. Companion build automation and a clean-clone unified packaging task remain work to finish before release. Use the existing Hybrid Foundation Test Prism instance for the installed test build.
