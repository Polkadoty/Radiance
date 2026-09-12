// SPDX-License-Identifier: GPL-3.0-only
// Original Square Skies medium sampling. No SEUS/Complementary implementation.
#ifndef SS_WATER_VOLUME_GLSL
#define SS_WATER_VOLUME_GLSL
#include "square_water.glsl"
struct SsWaterLight { vec3 radiance; float caustic; bool crossedWater; };
SsWaterLight ssSampleWaterSun(vec3 position,bool insideBoat,float cloudVisibility,bool focusCaustics) {
    SsWaterLight result;result.radiance=vec3(0.0);result.caustic=1.0;result.crossedWater=false;
    vec3 sun=celestialSunDirection();
    if(worldUBO.skyType!=1||sun.y<=0.03)return result;
    vec3 underwaterDir=-refract(-sun,vec3(0,1,0),1.0/1.333);
    vec3 start=position+underwaterDir*0.01;
    shadowRay.radiance=vec3(0.0);shadowRay.throughput=vec3(1.0);
    shadowRay.insideBoat=insideBoat?1u:0u;shadowRay.pad0=0u;
    shadowRay.ss5Mode=1u;shadowRay.ss5WaterT=1e20;
    // Probe nearest accepted boundary: water sides/solid geometry block it.
    // Any-hit does not terminate early in this mode; traversal order is irrelevant.
    traceRayEXT(topLevelAS,gl_RayFlagsNoOpaqueEXT,WORLD_MASK|PLAYER_MASK,0,0,0,start,0.001,underwaterDir,128.0,1);
    bool crossed=(shadowRay.pad0&0x80000000u)!=0u;
    float waterT=shadowRay.ss5WaterT;
    vec3 submergedTransmission=shadowRay.throughput;
    if(!crossed||waterT>127.99)return result;
    result.crossedWater=true;
    vec3 exitPosition=start+underwaterDir*waterT;
    float depth=max(exitPosition.y-position.y,0.0);
    float footprint=max(0.03,length(position)*0.0015);
    if(focusCaustics)result.caustic=ssWaterCausticReceiver((position+vec3(worldUBO.cameraPos.xyz)).xz,worldUBO.gameTime,sun,depth,footprint);
    // Start the air ray above the real surface, toward the AIR sun direction.
    shadowRay.radiance=vec3(0.0);shadowRay.throughput=vec3(1.0);
    shadowRay.insideBoat=insideBoat?1u:0u;shadowRay.pad0=0u;shadowRay.ss5Mode=0u;shadowRay.ss5WaterT=1e20;
    uint mask=WORLD_MASK|PLAYER_MASK;if(VPT_CLOUD_MODE==1u)mask|=CLOUD_MASK;
    traceRayEXT(topLevelAS,gl_RayFlagsNoneEXT,mask,0,0,0,exitPosition+vec3(0,0.025,0),0.001,sun,1000.0,1);
    if(cloudVisibility<0.0)cloudVisibility=volumetricCloudLightVisibility(position+vec3(worldUBO.cameraPos.xyz),sun,max(VPT_VOLUMETRIC_CLOUD_LIGHT_STEPS,1),0.175);
    vec3 air=applySampledLightChroma(shadowRay.radiance,sun)*(1.0-skyUBO.rainGradient)*cloudVisibility;
    result.radiance=air*submergedTransmission*exp(-ssWaterExtinction()*(waterT+0.01))
                   *ssWaterInterfaceTransmission(sun)*result.caustic;
    return result;
}
// Surface/probe callers keep the full inverse-refraction caustic solver.
SsWaterLight ssSampleWaterSun(vec3 position,bool insideBoat,float cloudVisibility) {
    return ssSampleWaterSun(position,insideBoat,cloudVisibility,true);
}
void ssIntegrateWater(vec3 origin,vec3 viewDir,float distanceInWater,bool insideBoat,out vec3 transmittance,out vec3 scattering) {
    float distance=clamp(distanceInWater,0.0,SS_WATER_DISTANCE);
    vec3 sigmaT=ssWaterExtinction();transmittance=exp(-sigmaT*distance);scattering=vec3(0.0);
    if(distance<0.001)return;
    vec3 sun=celestialSunDirection();float daylight=smoothstep(-0.08,0.18,sun.y);
    vec3 biome=clamp(worldUBO.fogColor.rgb,vec3(0.0),vec3(1.0));
    vec3 ambient=mix(vec3(0.015,0.045,0.065),biome*0.11,0.35)*mix(0.12,1.0,daylight);
    scattering=ambient*(vec3(1.0)-transmittance);
    if(SS_WATER_SHAFTS==0||worldUBO.skyType!=1||sun.y<=0.03)return;
    vec3 lightDir=-refract(-sun,vec3(0,1,0),1.0/1.333);
    float phase=min(henyeyGreenstein(clamp(dot(viewDir,lightDir),-1.0,1.0),0.62),1.0);
    vec3 sigmaS=vec3(0.012,0.019,0.023)*max(SS_WATER_DENSITY,0.05);
    // Deterministic stratification gives finer near-camera segments without
    // frame-random caustic sparkle. Exact homogeneous integration per segment.
    uint h=uint(gl_LaunchIDEXT.x)*1664525u+uint(gl_LaunchIDEXT.y)*1013904223u;h^=h>>16u;
    float jitter=0.25+0.5*float(h&65535u)/65536.0;
    vec3 prefix=vec3(1.0),shafts=vec3(0.0);
    // One distant-cloud query per camera ray, while local water/scene visibility
    // remains per segment. Cloud shadows vary slowly over this short medium.
    float cloudVisibility=volumetricCloudLightVisibility(origin+vec3(worldUBO.cameraPos.xyz),sun,max(VPT_VOLUMETRIC_CLOUD_LIGHT_STEPS,1),0.175);
    if(cloudVisibility<=0.0)return;
    // Budget visibility rays by water path length. The user setting is a cap;
    // two samples suffice for short paths, with one extra per six blocks.
    // Preserve the complete interval and exact Beer-Lambert segment weights.
    int sampleCount=min(max(SS_WATER_STEPS,2),max(2,int(ceil(distance/6.0))));
    for(int i=0;i<sampleCount;++i) {
        float a=float(i)/float(sampleCount),b=float(i+1)/float(sampleCount);
        float nearT=distance*a*a,farT=distance*b*b,dt=farT-nearT;
        // Medium shafts use unfocused sunlight. The expensive sharp caustic
        // pattern remains on receivers; recomputing it at every empty-water
        // sample costs heavily and aliases at low integration sample counts.
        SsWaterLight incident=ssSampleWaterSun(origin+viewDir*mix(nearT,farT,jitter),insideBoat,cloudVisibility,false);
        vec3 stepT=exp(-sigmaT*dt),integral=(vec3(1.0)-stepT)/sigmaT;
        shafts+=prefix*max(incident.radiance,vec3(0.0))*sigmaS*phase*integral;
        prefix*=stepT;
    }
    scattering+=softClampVolumetricLight(shafts*max(SS_WATER_SHAFT_STRENGTH,0.0));
}
#endif
