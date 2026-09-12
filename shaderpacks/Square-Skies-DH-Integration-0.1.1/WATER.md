# Water prototype 0.2

This version extends the original square-cloud pack with a readable, vanilla-aligned underwater treatment. It keeps flat Minecraft water geometry by default, with procedural optical detail beneath the surface.

**Not tested in Minecraft.** All new effects are implemented and compiled, but their final appearance and performance require runtime verification.

## Added behavior

- **Underwater color with distance:** RGB Beer-Lambert extinction preserves nearby warm colors and gradually filters them toward blue-green. It replaces the baseline's immediate multiplication by `(0, 0.48, 0.65)` for submerged cameras. Sky misses use the configured medium range instead of zero fog distance.
- **Occluded underwater sun shafts:** 24 configurable samples per pixel query the actual scene shadow ray, then integrate single-scattered sunlight with a forward phase function and RGB extinction. Sunlight is refracted toward the vertical using a flat-interface Snell approximation. Shafts require daytime Overworld sunlight; terrain, water transmission, and cloud shadows affect visibility.
- **Bounded refractive caustics:** water shadow hits estimate local focusing from the area Jacobian of three rays refracted through small procedural ripples. The effect fades with depth and sun angle and has a bounded contrast to avoid bright spikes. It works with the default flat water mode using Radiance's water material flag.
- **Denoiser-aware composition:** attenuation is applied to diffuse/indirect/specular/refraction branches before output. Scattering is routed through the existing emission/clear buffers, and the fog-already-composed sentinel avoids the native NRD underwater tint being applied again.

These are original implementations. Complementary Reimagined is the aesthetic reference, with no reused Complementary shader code or textures.

## Settings

| Water control | Default | Meaning |
|---|---:|---|
| Improved underwater presentation | On | Enables the new RGB medium and water shadow treatment |
| Ray-occluded sunlight shafts | On | Adds scene-shadowed underwater scattering |
| Shaft samples per pixel | 24 | Lower to 8 or 12 if expensive |
| Absorption/scattering density | 1.0 | Higher gives murkier, shorter visibility |
| Shaft strength | 1.0 | Artistic scale of direct water scattering |
| Refractive caustic contrast | 0.65 | Zero disables the local focusing variation |
| Maximum medium distance | 64 blocks | Bounds camera-medium integration |

Keep native cloud temporal accumulation off for initial testing. The pack has no dedicated temporal filter for underwater shafts. The air-volume mode remains the baseline default; changing that separately is optional.

## Specific limitations

This is not photon-mapped caustics or a full multiple-scattering fluid renderer. Local focusing does not solve overlapping caustic paths, full energy redistribution, or refractive light travel through multiple water/air boundaries. Shadow rays remain straight after they cross the modeled interface. Procedural caustic ripples do not displace the default flat water surface, and selecting the baseline FFT surface mode uses a different ripple field.

The camera medium extends to the first primary surface or the configured distance. It is not a general medium stack for nested fluids, secondary refracted paths, partially submerged objects, waterfalls, air pockets, or multiple water surfaces. The top-face shadow treatment approximates the water column from the ray origin to the detected water face; unusual water arrangements can be inaccurate. Clear water also has a small ambient term that is not locally sky-occlusion tested.

Full-resolution shafts add up to 24 shadow rays per submerged pixel at defaults. No RTX 4090 performance number is available. Stable per-pixel sample jitter avoids random frame glitter but can expose sampling bands or screen-space noise during movement. DLSS/NRD temporal behavior and reflection/refraction splitting still require game testing; this prototype does not fix a renderer crash or a general high-bounce artifact.

## Validation and first runtime check

All 46 default shader/pass variants passed Vulkan 1.4 `glslc` and `spirv-val`. The two changed executable shaders also passed with the new water effects disabled, and with maximum water sampling/distance/density plus the native FFT water mode enabled. Structural checks passed for 54 attributes, 19 passes, and 15 textures. Reports are in `validation/`.

The math checks cover constant-light medium integration, diffuse/split composition algebra, and flat-interface caustic normalization. They cannot prove live denoiser consistency or appearance.

When the port is ready for a visual test, use a shallow sunlit pool with a stone or sand floor, a partly shaded bank, and a deeper section. Compare above/below water, noon/sunset/night, shafts on/off, and caustics at 0/0.65. Check camera movement, the surface boundary, held items, colored blocks, and available denoiser modes. Record any bright edge artifacts before raising bounce counts.
