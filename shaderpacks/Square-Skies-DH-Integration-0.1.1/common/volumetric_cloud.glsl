#ifndef VPT_VOLUMETRIC_CLOUD_GLSL
#define VPT_VOLUMETRIC_CLOUD_GLSL

// Square Skies modification, 2026-09-05, GPL-3.0-only.
// Keep MCVR's atmosphere and lighting helpers. Replace only public cloud hooks.
#define applyVolumetricCloudBudgeted vptOriginalApplyCloudBudgeted
#define applyVolumetricCloud vptOriginalApplyCloud
#define volumetricCloudLightVisibility vptOriginalCloudLightVisibility
#include "volumetric_cloud_impl.glsl"
#undef applyVolumetricCloudBudgeted
#undef applyVolumetricCloud
#undef volumetricCloudLightVisibility
#include "square_clouds.glsl"

#endif
