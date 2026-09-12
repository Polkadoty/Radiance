# Square Skies 0.7 — Three Flat Layers

Replaces the rejected height wave with **three distinct flat cloud sheets**. There is no vertical warp, slope or height-wave control. Each sheet has a constant base altitude, its own sparse deterministic pattern and its own cloud cell size. Rounded joined edges and the subtle flat thickness tiers remain.

Select **Square Skies 0.7 - Three Flat Layers** from **Square-Skies-Prototype-0.7.zip**. Keep previous archives. The new filename gives this version fresh settings. This authoring task has not installed the pack live or measured its GPU performance.

| Sheet | Base altitude | Cell size | Coverage control |
|---|---:|---:|---:|
| Lower | 192 | 12 | 0.36 |
| Middle | 232 | 16 | 0.32 |
| Upper | 280 | 22 | 0.29 |

Altitude spacing controls default to 40 and 48 blocks. The lower altitude and cell/coverage controls remain, and the middle/upper sheets have their own size and coverage controls. Spacing stays greater than the filtered cloud thickness throughout the exposed settings, so sheets cannot intersect. In a 3.2 km square CPU occupancy sample, the three sheets covered about 31%, 25% and 22% of their respective planes, with little correlation between patterns. About 59% of that overhead area contained at least one sheet. These are sampled occupancy statistics, not screen coverage predictions. See the included layer-layout.png for a plan view of the same world area in each sheet.

Softer day/night lighting, restrained moonlight and native atmospheric haze from 0.6 remain. Water implementation files are byte-identical to 0.5.1/0.6, preserving the adaptive six-sample underwater cap and full receiver caustics. No SEUS or Complementary source/assets were copied.

Rays intersect the three flat vertical intervals and sort them front to back. That order reverses correctly when looking down from above and selects the appropriate sheets when the camera is between or inside them. One accumulated transmittance carries through the entire query; an opaque near cloud can terminate the remaining work. View, reflection and cloud-shadow queries use the same sheet geometry and occupancy seeds. Cloud rendering retains the existing miss-only integration, so a real geometry hit occludes the environment contribution. It remains an environment effect rather than cloud geometry in the scene acceleration structure.

The sky-cloud range remains independently bounded to 2,048 blocks, as in previous packs. Native primary/secondary TLAS trace lengths are geometry search budgets and remain unchanged; they do not clip the environment's cloud distance. There is no new gl_RayTmaxEXT cutoff in reflected or primary sky clouds.

Aggregate cost is bounded across all three sheets:

- Default view query: at most 256 cell visits and 768 density samples total; four cached local light queries per contributing density sample. Lower native view quality can reduce both limits. The maximum sample control ranges from 128 to 2,048.
- Cloud-shadow query: shared 16–48 cell visits and 128–384 density samples, depending on the caller's requested quality.
- One native eight-point atmosphere integration for the whole view query, plus at most one direct atmospheric transmittance lookup per intersected sheet.
- Intervals are conservatively clipped before either work limit can be reached, with smooth distance and aggregate slab-distance fades. Sampling retains a fixed phase within each connected sheet, across every DDA boundary. No 16,384-iteration inner loop or three independent work budgets remain.

The caps are upper bounds, not a performance prediction. Three sparse sheets can still cost more than a single sheet; early opaque termination and thin, empty-space-skipped intervals reduce typical work. At low quality or very shallow angles the budget fade can reduce distant clouds/shadows. Local cloud self-shadowing still uses four samples within its own sheet; it does not evaluate upper-sheet shadows on lower-sheet cloud lighting. Terrain sunlight visibility does traverse all intersected sheets. Haze remains the native-derived effective homogeneous approximation documented in 0.6.

Validation passed:

- Actual native MCVR C++ parser: 69 attributes, 19 passes, no SHARC requirement.
- All 46 default shader/SPIR-V configurations; ray generation, world miss and shadow miss also passed minimum/maximum quality, coverage and geometry configurations.
- 324 flat-interior checks: exactly zero horizontal density gradient. There are no wave-derived normals or cell-dependent base heights.
- 600 joined-cache boundary checks: exactly matching density gradients and all four local-light samples on either side. Analytic gradient error stayed below 1.90e-8.
- 3,400 float32 traversal cases plus 900 extreme-geometry/range cases: sorted, disjoint intervals, every sample assigned once, no cell/sample cap exceeded. Includes below/between/above cameras, axis-aligned rays and nearly horizontal directions.
- 240 full RGBA transport comparisons under different grid partitions: zero difference; independent depth sorting and transparent-layer composition also agreed exactly. The final sample-bin contribution approaches zero smoothly before the cap.
- Water file identity checks passed. Source hashes, default/extreme compiler reports, parser output and the new CPU tests are included.

These are CPU numerical tests and offline shader checks, not GPU execution or Minecraft screenshots. Full transport tests use connected controlled-density sheets and fixed incident light to isolate ordering/seam errors; seeded occupancy is tested separately. Actual sky appearance, reconstruction behavior and frame time require runtime testing.

Suggested runtime check: confirm the new middle/upper controls and shared sample cap appear; inspect all three layers from below, fly between 200–230 and 240–275, then look down from above 290. Inspect cloud reflections and horizon continuity. Compare daytime, sunset and starry-night lighting, then underwater FPS at the same resolution and settings.

To rerun the new CPU test after extraction: `python tools/check_clouds_v07.py --build <scratch-directory>` (NumPy and Pillow required). The bundled validate.py and check_native_parser.py retain their Vulkan SDK/MCVR/MSVC setup instructions. Earlier validation files describe their named historical versions. Current source is in common/square_clouds.glsl; all licenses and provenance are retained.
