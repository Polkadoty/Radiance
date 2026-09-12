# Source and licensing

Prepared 2026-09-05. The source ZIP is the deliverable; it contains no precompiled GPU binary.

## Radiance / MCVR baseline

Source: [Minecraft-Radiance/MCVR](https://github.com/Minecraft-Radiance/MCVR), local revision `9905c81b1999f5845bf66d13501d371c16adf561`. The copied source directories were clean when inspected.

Copied `src/shader/world/ray_tracing/internal/vanilla-pt`, `src/shader/util`, and `src/common`, following the native pack staging layout in `src/shader/CMakeLists.txt`. All baseline source and textures used by this pack are included. No changes were made to the original repositories.

The MCVR project is primarily GPLv3. Its original notice is retained in `licenses/MCVR-LICENSE.md`; the full GPLv3 text is `LICENSE.txt`. New Square Skies code and pack modifications are distributed under GPL-3.0-only. MCVR authors retain their rights in the baseline.

Retained third-party exceptions:

- `util/random.glsl`: NVIDIA copyright 2022-2024, Apache-2.0, original header retained. Full license: `licenses/Apache-2.0.txt`.
- `util/disney.glsl`: MIT, copyright 2019 Asif Ali with Radiance modifications. Complete MIT permission and warranty notice retained in the file.

The current local SHARC dependency has a separate NVIDIA RTX SDK license. SHARC source is not bundled. The `sharc` config entry and resolve shader were omitted, so the native loader does not initialize its SHARC runtime for this pack. Conditional inactive SHARC integration code in unchanged baseline sources is retained.

## Original prototype changes

- `common/square_clouds.glsl`: original deterministic grid field, analytic box traversal, cuboid appearance, cloud shadow visibility, sunset tint, and silver lining.
- `common/volumetric_cloud.glsl`: public-hook adapter. MCVR's original cloud implementation remains available under private renamed entry points for its atmosphere/lighting helpers; the new hooks perform cloud rendering.
- `configs.json`: independent name, new controls/defaults, irrelevant legacy shape controls removed, SHARC disabled.
- `lang/en_us.lang`: new labels and budget descriptions.
- Documentation, source manifest, and offline validation scripts/reports.

## Aesthetic reference

The user's requested visual direction was Complementary Reimagined-style square clouds. Reviewed the [official Complementary Reimagined repository](https://github.com/ComplementaryDevelopment/ComplementaryReimagined) and its [Complementary License Agreement 1.6](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/main/License.txt) on 2026-09-05. That license places conditions on modified redistribution, including a separate licensing model. No shader implementation or textures were copied, translated, or adapted from Complementary. The prototype instead uses an original implementation of the general square-cloud aesthetic.

This is not a Complementary port or an endorsed product. It does not promise visual parity with Complementary Reimagined.

## Version 0.2 water additions

Original `common/square_water.glsl` and `common/square_water_volume.glsl` implement RGB extinction, a single-scatter water medium, ripple refraction, and bounded Jacobian focusing. `world/world.rgen` now calls the water medium for submerged cameras and routes attenuation/scattering through native radiance buffers without the legacy underwater fog tint. `world/shadow.rahit` identifies flagged water in the default flat mode and applies the new absorption/caustic factors. Configuration and English labels add seven controls. `WATER.md` documents approximation and runtime limits.

Version 0.1 is preserved as a separate ZIP. No Complementary source or assets were added in version 0.2. The above MCVR baseline and its licensing remain unchanged. This prototype has not been run in Minecraft.

## Version 0.3 revision

Prepared after the user's v0.2-versus-Advanced runtime screenshots. No repository or active shaderpack files were edited. `common/square_clouds.glsl` now uses vanilla-scale defaults and adjusted opacity/shading. `common/square_water.glsl` shares the original ripple field with the surface normal and tighter bounded caustic response. `world/default.rchit` and `world/no_height.rchit` add continuous world-space water normals and a grazing reflection constraint. `world/default.rahit` limits the water self-hit exclusion by distance. Categorical water-flag reads use base mip in changed paths. `world/shadow.rahit` records diagnostic metadata in the existing unused pad field, preserving its payload layout. `world/world.rgen` uses camera-authoritative medium composition and adds optional raw-HDR diagnostic views. Configuration uses fresh ss3 names and disables the legacy FFT path. New geometry/property tests and primary gallery links are included.

The Minecraft 1.21.1 cloud dimensions and enum values were inspected in the user's local source JAR. Minecraft source was not copied into this pack. The user's supplied screenshots were inspected but are not bundled. The official Reimagined gallery is linked as a visual reference; no Complementary implementation or media was copied. v0.3 remains runtime-untested.

## Version 0.3.1

Configuration-only repair of duplicate definition aliases; shader sources unchanged from v0.3. Added actual native-parser regression harness and loader failure evidence. Runtime appearance remains unverified.

## Version 0.4

Original compact-kernel cloud density filtering, analytic density gradient, opacity integration and transmission-driven cloud lighting in common/square_clouds.glsl. Based on the aesthetic of three user-provided reference screenshots, which are not redistributed. No Complementary code or textures were used. The 12-block grid and water shader source remain unchanged. Native parser and independent numerical validation scripts are included.

## Version 0.5

Original multi-scale optical water height field, analytic gradient/Hessian, finite-footprint filtering, inverse refractive focusing, and two-leg underwater sunlight sampling. Public Sonic Ether E8 dev/release descriptions informed desired behavior, not implementation reuse. No SEUS code/assets were downloaded or copied. Changed files: common/square_water.glsl, common/square_water_volume.glsl, world/default.rchit, world/no_height.rchit, world/shadow.rahit/.rchit/.rmiss, world/world.rgen, world/clouds.rchit (payload initialization only), util/ray_payloads.glsl, configs.json and language. The cloud density source is unchanged from v0.4. See README for source links, validation and physical approximations.

## Version 0.5.1

Original cloud traversal/transport repair in common/square_clouds.glsl: explicit integer DDA progression, ray-global quadrature, regularized pointwise lighting and bounded cached local light integration. Parent-task water-performance contribution merged from work/square-water-performance/candidate/common/square_water_volume.glsl, with sample-cap default/range changed in configs.json and fallback in square_water.glsl. Surface caustic/wave implementation unchanged. Transport tests compare the continuous connected field under different grid partitions and reproduce old float32 stalls; runtime validation remains required.

## Version 0.6

Original changes in common/square_clouds.glsl: continuous bounded height warp with analytic chain-rule density gradients, moderated cloud-only lighting and lunar response, and per-depth aerial perspective derived from the retained native integrateAtmosphereSegment helper. Five controls and English labels added. Native atmosphere code itself is unchanged from the licensed base. common/square_water.glsl and common/square_water_volume.glsl are byte-identical to 0.5.1. Numeric transport tests exercise a warped connected island and mixed-height cache boundaries. Runtime appearance and performance remain to be evaluated. Earlier reports and notes remain historical; README.md describes this version.

## Version 0.7

Original three-sheet cloud implementation replaces the entire 0.6 height warp. Flat disjoint layer definitions have deterministic independent occupancy shifts and distinct cell sizes. Shared sorted intervals, cell/sample caps, and conservative smooth budget fades are used for view and cloud-shadow transport. Existing point lighting, native-derived atmosphere and water implementations are retained. Native miss evaluation and TLAS ray limits are unchanged. New source and numerical validation are original; no additional external code/assets were obtained.
