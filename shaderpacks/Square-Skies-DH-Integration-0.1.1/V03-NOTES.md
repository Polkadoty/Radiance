# v0.3 changes and runtime checks

## Confirmed from the runtime comparison

v0.2 clouds were oversized: 48-block square cells versus vanilla's 12, with much thicker layers. The user's comparison also showed rectangular reflection patches on v0.2 water. The user reported no visible underwater shafts or caustics. These reports supersede any visual expectations from offline compilation.

## Implemented corrections

- Cloud cells now default to **12 blocks**, base **Y=192**, top thickness **4/5/6 blocks**, and coverage **0.42**. Undersides are lighter and the optical depth makes centers more solid. The trace range is 2048 blocks with a 256-cell cap.
- Cloud shape controls, cloud mode, temporal toggle, and shadow toggle use fresh `ss3_*` attribute names. Old v0.2 settings cannot silently restore the 48-block scale. Cloud mode defaults to square cuboids and cloud temporal accumulation defaults off.
- The legacy FFT surface path is disabled by the world-pass definition. Old global water-mode settings cannot turn it back on. A small continuous **world-space ripple normal** replaces per-material water normals on upward/downward horizontal water faces. The block geometry stays planar.
- Ripple tilt is limited near grazing views so reflected rays stay on the correct side of the flat surface. This avoids a class of erroneous reflections into submerged geometry.
- Water material flags are sampled at base mip in the changed material/self-hit paths. Flags are categorical bit fields rather than interpolated colors.
- The native water self-hit exclusion previously ignored every later water face for the entire first transparent branch. v0.3 limits that exception to hits closer than 0.005 blocks so separate water interfaces can still be intersected.
- Underwater composition follows the camera submersion flag even if a later ray changes the fog-skip state. Medium distance comes explicitly from the selected primary segment. Water density defaults to 0.6, and caustic focusing uses a tighter bounded Jacobian response.

The underwater submersion enum and Overworld sky enum were verified against the actual 1.21.1 sources: both use value 1 for the relevant state. DLSS uses the pack's raw HDR output directly, so the NRD fog sentinel is not an explanation for missing shafts under DLSS. A definitive runtime cause for the user's missing shafts/caustics is **not yet established**. This version includes probes to determine which rendering path is failing or too faint.

## Runtime probes

The shader settings contain **Prototype diagnostics (normally Off)**. They are intended for a brief runtime check using the DLSS pipeline. Return the setting to **Off** afterward. These displays write the raw HDR output, so the current exposure/tonemapping still affects their appearance.

| Probe | Expected result | Interpretation |
|---|---|---|
| Camera water flag | Green underwater, magenta outside | Confirms the camera medium branch can run |
| Camera water path length | Grayscale, increasing with distance to the primary surface | Black everywhere underwater suggests a near-zero primary segment; maximum brightness means range cap/sky miss |
| Water sunlight visibility | Grayscale when a shadow probe crossed water; magenta tint when it did not | Black means little/no direct sunlight reaches the probe, even if water was detected |
| Floor caustic and water-crossing probe | Varying grayscale on submerged floor; magenta means no recognized water crossing | Confirms the local caustic helper is reached; it does not by itself prove final lit caustics are visible |

Test the caustic probe while looking at the **floor from underwater**, in daytime, with a clear route to the sun. Use the sunlight visibility probe separately because a caustic factor can be computed along a subsequently blocked ray. Stable green in the first probe plus useful grayscale in the last two narrows any remaining issue to contrast, integration, or denoising rather than an inactive water path.

## Validation

- 46 default shader/pass variants: Vulkan 1.4 `glslc` compilation and `spirv-val` pass.
- Caustic-probe raygen variant and four water-disabled/zero-ripple shader variants: pass.
- Structural checks: 55 attributes, 19 passes, 15 textures; file references, execution references, defaults and bindings pass.
- 188 independent brute-force cloud-ray comparisons: pass at the new geometry scale; maximum accumulated path-length difference 0.156 blocks from the small traversal epsilon.
- 3,079 water property cases: finite/unit normals, continuity across world coordinates, grazing reflection hemisphere, bounded self-hit exclusion, and flat caustic normalization pass.

No Minecraft run, native loader execution, visual quality measurement, or FPS benchmark was performed for v0.3. Full fluid stacks, refractive light paths across multiple interfaces, photon-traced caustics, cloud/terrain depth intersection, and dedicated shaft temporal filtering remain outside this prototype.
