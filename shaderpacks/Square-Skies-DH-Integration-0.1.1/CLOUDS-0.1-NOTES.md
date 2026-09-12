# Square Skies Prototype 0.1

An experimental Radiance shaderpack focused on attractive square clouds: broad cuboid islands, stepped tops, shaded blue undersides, gentle sun shadows, warm sunset tint, and a restrained silver lining. Based on Radiance/MCVR Vanilla PT. No PBR resource pack required.

**Status: offline validated; not tested in Minecraft.** This is a separate optional prototype for the current Radiance 1.21.1 port and the MCVR shaderpack API inspected on 2026-09-05. Appearance, performance, loader acceptance, and GPU pipeline compatibility still need an in-game check.

## Try it later

1. Copy `Square-Skies-Prototype-0.1.zip` into the intended Minecraft instance's `shaderpacks` directory. Keep the ZIP intact.
2. Open Radiance's shaderpack screen and select **Square Skies Prototype 0.1**. This is a Radiance Vulkan pack; it is not an Iris/OptiFine pack.
3. In shader settings, select **Square cuboid clouds** for Cloud mode. This uses the native volumetric mode internally. Existing saved settings may override pack defaults.
4. Start with clear Overworld weather, normal textures, and the default settings. Test noon, sunset, night, water reflections, and a high viewpoint. Return to Vanilla PT through the same screen if needed.

For Prism installations using directory junctions, use the updated Radiance port that resolves its native directory to a physical path. The shaderpack itself does not fix the native ZIP extraction issue. Do not replace built-in pack ZIPs.

## Controls

| Control | Default | Purpose |
|---|---:|---|
| Cell size | 48 blocks | Size of each square footprint |
| Base altitude | Y=224 | Flat cloud base |
| Maximum thickness | 28 blocks | Three stepped top heights |
| Clear-weather coverage | 0.55 | Amount of cloud; rain increases coverage |
| Wind speed | 0.8 blocks/s | Slow horizontal drift |
| Opacity per block | 0.16 | Softens thin edges while keeping solid centers |
| Warm sunset tint | 0.35 | Subtle warm light near sunrise/sunset |
| Silver lining | 0.30 | Forward light at sun-facing edges |
| Traversal budget | 128 cells | Upper limit for primary cloud tracing |

Native view/light budget controls remain available. Their values are multiplied by four for grid traversal and capped; raising the main traversal cap alone may therefore have no effect. Cloud temporal accumulation defaults off. Native cloud sun-shadow toggle remains available. Parallax defaults off and water uses the native block-surface mode; normal textures work without a PBR pack.

## Hooks and limits

- `applyVolumetricCloud` and its budgeted form use exact ray/box intersections in a procedural XZ grid. The native sky-miss path, eligible reflections, and star occlusion consume their radiance and transmittance.
- `volumetricCloudLightVisibility` supplies gentle matching cloud shadows through the native sun/moon material-lighting hook. This prototype does not add a volumetric light-beam pass.
- Native atmosphere, world path tracing, fog composition, and post-render passes remain the Vanilla PT baseline. The new tint and silver lining affect clouds only.
- This is a sky-miss integration: cloud boxes do not enter the scene acceleration structure. Cloud depth, cloud/terrain intersections, flight through the layer, transparency sorting, and nearby mountain overlap are not fully resolved. Geometry may stay in front of clouds even where the modeled layer would be nearer.
- A flat layer fades out toward 4096 blocks. Low budgets, small cells, or grazing rays may exhaust the traversal cap. Wind resets when the native normalized shader clock wraps, roughly every 20 minutes. Large world coordinates may lose float precision.
- SHARC caching is intentionally omitted from this pack. Runtime cost and image convergence can differ from the built-in pack with caching enabled. No FPS claim is made for the RTX 4090.

## Validation

All **46** referenced shader/pass variants compiled with Vulkan SDK 1.4.328.1 `glslc`, targeting Vulkan 1.4, and passed `spirv-val`. Structural checks covered 47 attributes, 19 passes, 15 textures, defaults, file references, runtime bindings, and execution pass references. These are structural checks against the inspected schema, not an invocation of the C++ loader.

An independent CPU brute-force box reference matched grid traversal on **188** rays, including vertical, horizontal, negative-coordinate, boundary, inside-layer, and above-layer cases. Maximum total path-length difference was 0.114 blocks from the traversal epsilon (tolerance 0.5 blocks). This verifies traversal logic rather than rendered images.

The source archive includes validation reports and scripts. To repeat offline checks from the extracted pack directory with Python 3 and a Vulkan SDK:

```powershell
python tools/validate.py --pack . --sdk C:/path/to/VulkanSDK
python tools/check_geometry.py
```

The compiler check injects native-style execution-buffer declarations and expands pack default attributes. It does not create a Vulkan pipeline or validate all runtime settings combinations.

See `PROVENANCE.md` and `LICENSE.txt` for source and licensing. No Complementary shader code or assets are included, and this pack is not affiliated with Complementary Development.
