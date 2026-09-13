# Native HUD migration — 2026-09-12

## Behavior

The companion routes recognized HUD shaders and registered mesh/shader vertex layouts into Radiance's native Vulkan overlay renderer. Unsupported main-target draws enter an OpenGL segment. Before the next supported draw, that segment is copied to a distinct registered Vulkan texture and composited in order. Private framebuffer draws remain with their owner.

The HUD snapshot pool is capped at two full-resolution textures. After a second compatibility segment begins, remaining draws stay in that segment for the frame. This avoids unbounded texture allocations and transfers when a mod interleaves custom and ordinary draws. Screenshots and draw counters are correctness evidence, not FPS benchmarks.

`radiance.nativeHud` defaults to true from native-hud.3's map-fix build onward. Set `-Dradiance.nativeHud=false` to restore the previous native-hotbar/compatibility-HUD behavior. The first .3 test had been launched without its opt-in flag after Prism rewrote the instance configuration; it does not validate native HUD routing.

Xaero's full-screen map uses custom vertex formats outside the Gui HUD scope. Pressing M previously reached Constants.VertexFormats with an unknown layout and crashed. A separate Screen.renderWithTooltip scope now draws Xaero map screens to their own compatibility target and publishes one independent texture. Its texture is separate from HUD snapshots because overlay execution is deferred. Other screens retain their previous renderer.

Overlay clears override and restore scissor, color, depth and front/back stencil write masks. Targets and textures are released before native renderer shutdown.

## Observed runtime

- Main JAR remains Hybrid7, with approved connected-texture, DH/ocean, DLSS and terrain changes. No native DLL, world shader or texture pack changes.
- The .3 map-fix companion built and loaded in the full Aeronautics test.
- At 21:56:40, position_tex, entity_translucent_cull, gui_overlay and text shaders logged native Vulkan routing. Later GUI, position_color, entity_cutout and glint routes were observed.
- At 21:57:10, counters showed 1,791 HUD frames, 53,795 Vulkan draws, 126,750 fallback draws and 2,464 segment copies; snapshot pool stayed at two. These cumulative counts include startup/transitions and are not a steady-state timing comparison.
- Xaero map-screen composition activated at 21:57:28. The user confirmed M opened/closed the map and HUD/chat/inventory looked correct. A supplied screenshot also shows the HUD, minimap and chat.
- The .4 experiment permitted xaerolib/pos_tex_alpha_test_pre, position_color, position_color_no_alpha_test and position_color_tex names after inspecting their ordinary uniforms/samplers. Both mesh and shader layouts still had to be registered. Startup GPU checks passed, but the user reported a blank minimap. Runtime showed pos_tex_alpha_test_pre went native while both position_color variants remained in GL due to custom layouts.
- The minimap's source attachment is GL-only. Standard shader syntax/layout does not imply that sampled textures are available to Vulkan. native-hud.5 removes the Xaero allowance and restores the .3 map-fix routing, retaining more explicit route diagnostics. The full-screen M-key map fix stays in place.
- .5 compiles successfully. Final artifact SHA-256: `fdbab2e2ed17de7331a566c2dab3eecf78ca7384a6592dd3837e163190108afa`. Installation and final visual recheck are pending.

## Remaining scope

Private minimap/framebuffer rendering, unregistered vertex layouts, arbitrary raw OpenGL draws, and custom render-state interactions are not fully native. General vertex-layout registration plus native offscreen color/depth/stencil targets are needed to remove those fallbacks. Flywheel and arbitrary world producers are separate geometry integrations, not solved by this HUD work.

Bright horizontal leaf shelves remain visible in the user's screenshot. No leaf shading changes are included in this checkpoint.

## Build

Run `tools/build-hybrid-companion.py` with explicit `--main-jar`, `--libraries`, `--compile-deps`, `--jdk` and a fresh `--output` directory. It compiles all companion Java sources, packages resources, reads the version from NeoForge metadata and writes an artifact hash manifest. Runtime jars, local paths in generated argument files, and launcher data are not source checkpoints.
