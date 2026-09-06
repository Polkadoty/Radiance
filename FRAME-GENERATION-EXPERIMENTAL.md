# Experimental Frame Generation checkpoint

This checkpoint builds on the saved Minecraft 1.21.1 Fabric/NeoForge port. It adds an optional Streamline 2.12.0 DLSS Frame Generation/Reflex integration, disabled by default, along with shaderpack fallback diagnostics, texture reload synchronization fixes, JNI exception boundaries, and swapchain resource-retention fixes.

The full Windows Release native build and both Gradle builds passed. Packaged JAR CRCs, loader metadata, shader presence and NVIDIA plugin exclusion were checked. Isolated component tests cover camera/depth conversion, DLL rejection, JNI errors and frame-resource lifetime changes. Runtime generated frames have NOT yet been demonstrated. The reported texture-reload device-loss fix also awaits reproduction testing.

Do not treat this as the tested port release or install it into the user's ordinary modpack. Existing port/1.21.1 source and artifacts remain the stable comparison checkpoint. Distant Horizons and the Square Skies water/cloud prototype are separate work.

Streamline production plugins are external files under the instance's radiance/streamline directory. NVIDIA SR/RR libraries remain separate in radiance. No NVIDIA runtime DLLs are embedded in these JARs or committed to source. Hardware availability and successful rendering must be verified in runtime logs; plugin presence alone does not prove frame generation is working.

Next runtime checks: baseline with plugins absent; plugins present/FG Off; world FG On and nonzero generated-frame counters; HUD/hand quality; pause/focus toggles; resize; resource reload; save/quit. Test in a separate client when the GPU is available.

Matching native source: https://github.com/Polkadoty/MCVR/commit/bcebe7d (feature/frame-generation).


## FG.2 runtime checkpoint (2026-09-05)

Pins Streamline to local production2.12 plugins, includes both RR and FG NGX
search paths, and preserves Streamline ownership of device-wide NGX shutdown.
Latched runtime failures now clear the Java toggle with a diagnostic tooltip.
Frame-generation status requires actual generated presents and a valid
completion timeline; activation alone is not reported as success.

Full Windows native and NeoForge builds passed. A separate RTX4090 test
confirmed Ray Reconstruction initialization, availability, creation and
evaluation Success while FG reported status0, actuallyPresented2 and valid
completion waits. This is not a measured performance multiplier or a claim
of universal visual quality. Create/DH integration and complete shutdown
stress testing remain separate. NVIDIA runtime binaries are not committed.
