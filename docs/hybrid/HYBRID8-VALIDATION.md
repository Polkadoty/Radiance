# Hybrid8: connected materials and explicit overlay layouts

## Changes

Hybrid8 pairs a new MCVR core with native-hud.6. Java's overlay shader registry can now pass a custom vertex stride and attribute locations, formats and offsets instead of requiring a fixed enum entry. Known formats retain their previous path. The native boundary validates counts, offsets, duplicate locations, stride and device format support. Pipeline recreation retains explicit layouts alongside their shader definitions.

Two audited, untextured Xaero shaders (position_color and position_color_no_alpha_test) can use this path in the HUD. Layout support is cached per format. The minimap sampling shader remains on the working OpenGL segment because its source attachment is GL-produced. Private targets and the M-key map keep their existing compatibility path. This is not a complete native offscreen renderer or an FPS claim.

AuxiliaryTextures now discovers matching normal, specular and flag PNGs under optifine/ctm in addition to the existing textures folders. Hybrid7 allowed upload of CTM identifiers but did not include that directory in the prepared-resource scan; its earlier confirmed visual fix was the material/alpha-layer adapter, not proof that CTM PBR maps loaded.

Resource-pack example:

```text
assets/minecraft/optifine/ctm/path/7.png
assets/minecraft/optifine/ctm/path/7_n.png
assets/minecraft/optifine/ctm/path/7_s.png
```

Use the same namespace, directory and basename as each selected CTM tile. Missing maps retain neutral defaults. No base texture's material is automatically stretched across unrelated CTM variants. Optional _f maps can also be siblings. The user's new resource pack is not yet available for visual validation.

## Validation

- MSVC Release core build passed using the existing local Vulkan/vendor dependencies.
- NeoForge offline build and packaged native-resource verification passed.
- Startup on the actual test GPU passed custom padded/reordered vertex-layout registration, registration caching and malformed-descriptor rejection.
- Startup passed CTM normal/specular/flag PNG discovery, exact decoded pixels, mip availability and missing-map behavior, using an isolated prepared cache.
- Existing material-cache pixel/lifetime/repeated-upload probes passed.
- First launch exposed an unmapped reflection method name in the new test fixture. Replaced it with typed ResourceManager overrides, which Loom remaps.
- Second launch exposed the test shader sources being deleted before pipeline recreation. They now survive until JVM shutdown. Explicit layouts also persist across recreation.
- Final retry passed all new and existing startup probes, completed full resource loading and reached the title screen. ModernFix recorded startup completion at 22:50:53; the All of Create window was responsive. In-world HUD/minimap/resource-reload checks were requested from the user and remain pending.

The startup registration test does not draw or read back a native pixel. Visual testing is still required for Xaero color overlays, minimap content, M-key map, chat/inventory, resize and F3+T.

## Remaining rendering work

Native color/depth/stencil offscreen targets and texture synchronization are the next shared prerequisite. Shader syntax and a supported vertex layout alone do not make GL framebuffer attachments available to Vulkan.

Leaf shelf lighting remains under investigation; no world shader/material edits are included here. Opaque cutout pixels already terminate shadow rays. The supplied screenshots alone do not distinguish repeated alpha masks, geometric face normals, PBR reflection and shadow/visibility problems. A controlled same-tree material/shadow comparison is still needed.

Dynamic held-item and burning-entity lighting is not implemented by this checkpoint. First-person items use a camera-space HAND ray mask separate from world rays. A world-space dynamic emitter path should account for both hands and burning entities without making enlarged first-person geometry cast incorrect world shadows. Existing placed-block light sampling must be preserved.

Test backups and artifact hashes live in the local staging manifest; runtime DLLs, launcher files and world data are not part of source commits.

Installed main SHA-256: 36d873e4fa9a0b34d9262189593cd5f4d0b94a6f33c3ca111c287de575a031f5.
Companion SHA-256: d577e6c6425159ed16b05e573fbaad068aac97ce482f32d92e5e86eb6ad7d87c.
Native core SHA-256: 86d1833278160ae406a9683288fa0b84ec3952808020b526ebcb3c8239cea66e.
