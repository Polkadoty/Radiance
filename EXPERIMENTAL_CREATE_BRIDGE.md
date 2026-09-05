# Experimental Create rendering bridge checkpoint

This branch adds an opt-in persistent Vulkan mesh/instance scene for moving
geometry on the Minecraft 1.21.1 port. Meshes retain their BLAS across frames;
instance transforms carry camera-relative double precision and motion history.
Opaque/cutout materials are supported. Transparent surfaces, block entities,
Flywheel instance production and full Aeronautics compatibility remain unported.

The Java acceptance harness is enabled with `-Dradiance.syntheticMesh=true`.
It creates two rotating instances of one diamond-block mesh, initially four
blocks north of the camera. A mirrored nonuniform transform exercises normals.
Do not enable it while another producer owns the complete scene snapshot.

Validation: complete Windows native DLL and NeoForge build pass; JNI error,
packet/transform/revision and frame-retention checks pass. The create.1 JAR
failed startup because shader resources were missing. The corrected create.2
JAR includes 71 validated SPIR-V shaders and both built-in shaderpacks, and
the NeoForge check task now verifies the packaged runtime resources.

GPU acceptance of this mesh harness is still pending. This is a source
checkpoint, not a validated Create-compatible release. Distant Horizons,
frame generation and Square Skies are separate experiments. No NVIDIA
runtime binaries or personal test worlds are committed here.
