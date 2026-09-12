#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_GOOGLE_include_directive : require

#include "util/ray.glsl"

layout(location = 1) rayPayloadInEXT ShadowRay shadowRay;

void main() {
    if (shadowRay.ss5Mode == 1u) {
        // Any-hit may encounter far surfaces first: only the committed closest
        // hit matching the recorded nearest water interface validates the probe.
        shadowRay.pad0 = gl_HitTEXT == shadowRay.ss5WaterT ? 0x80000000u : 0u;
    }
}
