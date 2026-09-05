# Radiance 1.21.1 experimental port

This is an unofficial port of Radiance 0.1.5-alpha for Windows x64. Source is in `C:\GitHub\Radiance`, on branch `port/1.21.1`; the native renderer is in `C:\GitHub\MCVR`. This checkpoint excludes experimental Distant Horizons and frame-generation integration.

## Install

- Java 21 and Minecraft **1.21.1** are required.
- Fabric: Fabric Loader 0.18.3 and Fabric API 0.116.6+1.21.1; use the Fabric JAR.
- NeoForge: tested with **21.1.249**; use the NeoForge JAR. Do not install Fabric API in this instance.
- For NeoForge, set `earlyWindowControl = false` in the instance's `config/fml.toml` before launch. Its early OpenGL window cannot be reused by this Vulkan renderer.
- Install only one Radiance JAR in an instance. Use an isolated instance while testing compatibility with other mods.

The Prism instance **Radiance NeoForge 1.21.1 - Port Test** uses the packaged mod and a separate copy of the test saves. Its files are in `C:\GitHub\Radiance\prism-neoforge`. The Fabric and NeoForge development runs are in `run` and `neoforge\run`, respectively.

The mod JAR contains the built native renderer, shader packs, and bundled runtime dependencies. DLSS libraries are external: put `nvngx_dlss.dll` and `nvngx_dlssd.dll` in the instance's `radiance` directory. The test instances use NVIDIA library version 310.7.0.0. These NVIDIA DLLs are not included in the delivered JARs. PBR resource packs are optional. Initial testing used vanilla textures; the user has since enabled a separate LabPBR pack.

## Compatibility notes

- NeoForge chunk model-data snapshots are captured on the render thread before background mesh generation. Each job keeps an immutable matching origin; publication and chunk-slot relocation share a lock, and outdated positions are discarded. The packaged world now loads and subsequent logs have not repeated the old out-of-range chunk errors. The scheduler now spreads snapshots across frames, caps queued jobs, and prevents overlapping builds per slot. The user reports improved movement/loading spikes; no controlled before/after benchmark establishes the size of the improvement.
- World fog is cleared before drawing menus and HUD, matching vanilla 1.21.1. This corrected tinted/misshapen lettering, washed-out inventory blocks, and the character preview.
- `renderFirstPersonBody=false` in `radiance/options.properties` omits the full camera-entity model in first person while preserving the hand and third-person rendering. This compatibility workaround also removes first-person body shadows/reflections. Set it to `true` with the game closed to opt back into the original behavior; the user confirmed that the workaround removes the black body-shaped blocks on dry land. Proper first-person body shadows/reflections remain under investigation.
- Broad modpack compatibility and HDR output have not been validated. HDR support has not yet been ported.
- The packaged NeoForge client failed while loading its native shader pack. A diagnostic JNI boundary now reports the C++ exception to Java. The native ZIP extractor rejects junction ancestry; the port now resolves the renderer directory with `toRealPath()` before native loading. This fix is verified in the packaged client: shaders load and world rendering starts.
- The isolated C++ runtime replacement was eventually exercised and did not fix that startup failure. Existing Java installations were not modified.
- The official comparison is also prepared without a junction as **Radiance Official 1.21.4 - Direct Path Test** in Prism. Its upstream mod is unchanged; this new setup awaits launch.

## Current test status (2026-09-05)

- Packaged NeoForge loads the copied world. NVIDIA DLLs 310.7.0.0 were verified signed and loaded from the intended instance. Native diagnostics confirm NGX initialization, Ray Reconstruction availability, creation and evaluation all succeed. This verifies RR, not frame generation or a particular marketing model name.
- The missing 1.21.1 direct item glint path now uses the existing material glint wrapper. The user confirmed the purple rectangle around the enchanted axe is fixed.
- PlayerSkinTexture uploads now track the Radiance texture target. The latest authenticated world entry has no repeated negative-target skin upload error.
- The profiler chart previously crashed in the native QUADS-only index generator. Java now supplies indices for other overlay modes and selects a shader pipeline with matching topology. Both loader builds and 57 targeted index/topology checks passed; the profiler was subsequently enabled without a new crash in the observed session, although extended testing remains necessary.
- The separate Square Skies + Water v0.2 prototype was tested: clouds were oversized, water had hard-edged patches, and the user saw no underwater shafts/caustics. It is not part of this baseline; revisions are in progress. Distant Horizons and frame generation are separate work in progress; HDR remains unported.

- Native entity indices now accept triangle lists and general alternating triangle strips. The Release renderer build and 1,025 focused index cases passed. This additional native fix is included in the checkpoint artifacts but has not yet been tested in the running client.

## Build

Use JDK 21. From `C:\GitHub\Radiance`:

```powershell
$env:JAVA_HOME = 'C:\GitHub\tools\java21\jdk-21.0.12.1+1'
.\gradlew.bat build
.\gradlew.bat -p neoforge build
```

Fabric artifacts are in `build/libs`; NeoForge artifacts are in `neoforge/build/libs`. The installable files have the Minecraft version and loader in their names. Source JARs are not installable mods.

The native build uses the recursively cloned MCVR repository, Visual Studio 2022 C++ tools, CMake, Vulkan SDK 1.4.328.1, and the JNI headers generated by Radiance's Fabric `compileJava` task. The existing native build is configured in `C:\GitHub\MCVR\build` with Java root `C:/GitHub/Radiance`, `USE_AMD=ON`, and `MCVR_ENABLE_NRD=ON`. `USE_AMD` enables a portable image-layout path and also works on the tested RTX 4090.

```powershell
cmake --build C:\GitHub\MCVR\build --config Release --parallel 8
cmake --install C:\GitHub\MCVR\build --config Release
```

Install the native outputs into Radiance's resources before packaging the Java mod. The ignored native binaries and shader outputs must be rebuilt or restored on a fresh checkout. Preserve the upstream licenses when redistributing.

## Source checkpoint

Native renderer: https://github.com/Polkadoty/MCVR/commit/9669e62 (branch port/1.21.1). Build this matching native source before packaging. The mod checkpoint includes no DH, FG, HDR, or experimental shaderpack code.
