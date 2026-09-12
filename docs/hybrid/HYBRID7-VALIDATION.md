# Hybrid7 validation — 2026-09-12

## Runtime observed before the Continuity changes

- NeoForge build and packaged native-resource verification passed.
- An initial startup failed because the runtime installer required `core.lib`. Removing that build-only dependency allowed startup and a full modpack server session.
- Additional-section-geometry probe logged PASS 1/3, 2/3 and 3/3 on chunk workers. It checked client-thread event collection, a non-null captured region, section-local identity pose, PBR layer consumers and consistent buffer identity. No synthetic vertices were emitted.
- Native hotbar composition remained active. Three affected mod foliage models were reported as cutout instead of solid.
- The temporary automatic local-world runner did not start; the user joined their server. No automated rebuild/save/exit pass is claimed. The runner was removed from the final source.

## Connected-texture changes staged afterward

Installed Continuity 3.0.0 and Stay True use `method=overlay`, `layer=cutout` for top grass overlays on dirt paths. The Aeronautics LabPBR pack contains matching albedo/normal/specular images. A sampled overlay has 253 transparent pixels out of 256, identical in Stay True and the PBR override.

The installed Indigo BlockRenderContext implementation returns its original vertex consumer regardless of the requested RenderLayer. Radiance terrain compilation enters that single-buffer model path, so solid base blocks cannot preserve overlay cutout semantics. An optional mixin now requests the proper PBR layer only inside a scoped terrain build; other uses retain Indigo behavior. The scope restores previous state in a finally block.

AuxiliaryTextures previously loaded maps only from block/item/entity texture paths, skipping `optifine/ctm` sprites. Those sprites now use the same cached auxiliary-map loading and defaults as ordinary block textures.

The final build passed in 21 seconds with native-resource verification. Staging verified an unchanged core DLL and unchanged companion bytecode. Runtime application of the optional mixin and disappearance of the reported black/white overlay patches still require the next restart and visual check.

## Remaining visual questions

- Bright horizontal leaf shelves: sampled oak/birch specular maps encode no emission. No speculative material or shadow changes have been made.
- Additional callback geometry in empty sections and during Sable motion needs visual coverage.
- No comparative performance measurement or active frame-generation validation was performed during this run.
