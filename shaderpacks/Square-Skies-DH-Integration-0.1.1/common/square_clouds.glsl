// SPDX-License-Identifier: GPL-3.0-only
// Original Square Skies prototype, 2026-09-05. No Complementary code or assets.
#ifndef SQUARE_SKIES_CLOUDS_GLSL
#define SQUARE_SKIES_CLOUDS_GLSL

#ifndef SS_CELL_SIZE
#define SS_CELL_SIZE 8.0
#endif
#ifndef SS_BASE_HEIGHT
#define SS_BASE_HEIGHT 192.0
#endif
#ifndef SS_THICKNESS
#define SS_THICKNESS 6.0
#endif
#ifndef SS_COVERAGE
#define SS_COVERAGE 0.24
#endif
#ifndef SS_WIND_SPEED
#define SS_WIND_SPEED 0.8
#endif
#ifndef SS_EXTINCTION
#define SS_EXTINCTION 1.1
#endif
#ifndef SS_SUNSET_TINT
#define SS_SUNSET_TINT 0.35
#endif
#ifndef SS_SILVER_LINING
#define SS_SILVER_LINING 0.65
#endif
#ifndef SS_MAX_CELLS
#define SS_MAX_CELLS 256
#endif

#ifndef SS_CLOUD_BRIGHTNESS
#define SS_CLOUD_BRIGHTNESS 0.9
#endif
#ifndef SS_MOON_GAIN
#define SS_MOON_GAIN 0.18
#endif
#ifndef SS_AERIAL_STRENGTH
#define SS_AERIAL_STRENGTH 1.0
#endif
// Three flat sheets. Positive gaps exceed the maximum filtered thickness,
// keeping their support intervals disjoint for every exposed setting.
#ifndef SS7_MIDDLE_GAP
#define SS7_MIDDLE_GAP 192.0
#endif
#ifndef SS7_UPPER_GAP
#define SS7_UPPER_GAP 256.0
#endif
#ifndef SS7_MIDDLE_CELL
#define SS7_MIDDLE_CELL 11.0
#endif
#ifndef SS7_UPPER_CELL
#define SS7_UPPER_CELL 15.0
#endif
#ifndef SS7_MIDDLE_COVERAGE
#define SS7_MIDDLE_COVERAGE 0.22
#endif
#ifndef SS7_UPPER_COVERAGE
#define SS7_UPPER_COVERAGE 0.20
#endif
#ifndef SS7_MAX_SAMPLES
#define SS7_MAX_SAMPLES 768
#endif
#ifndef SS8_GROUP_SPACING
#define SS8_GROUP_SPACING 0.72
#endif
struct SsCloudLayer { float cellSize; float base; float coverage; ivec2 seed; int id; };
SsCloudLayer ssCloudLayer(int id) {
    SsCloudLayer l;l.id=id;l.base=SS_BASE_HEIGHT;l.cellSize=SS_CELL_SIZE;l.coverage=SS_COVERAGE;l.seed=ivec2(0);
    if(id>=1)l.base+=max(SS7_MIDDLE_GAP,SS_THICKNESS+8.0);
    if(id==2)l.base+=max(SS7_UPPER_GAP,SS_THICKNESS+8.0);
    if(id==1){l.cellSize=SS7_MIDDLE_CELL;l.coverage=SS7_MIDDLE_COVERAGE;l.seed=ivec2(941,-631);}
    if(id==2){l.cellSize=SS7_UPPER_CELL;l.coverage=SS7_UPPER_COVERAGE;l.seed=ivec2(-1277,1499);}
    return l;
}

const float SS_FAR = 2048.0;
const float SS_EPS = 0.002;

// Integer hash: repeatable for negative coordinates without texture assets.
float ssHash(ivec2 cell) {
    uint h = uint(cell.x) * 1664525u + uint(cell.y) * 1013904223u;
    h ^= h >> 16u; h *= 2246822519u; h ^= h >> 13u;
    return float(h & 0x00ffffffu) / 16777216.0;
}

float ssNoise(vec2 p) {
    ivec2 i = ivec2(floor(p));
    vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(ssHash(i), ssHash(i + ivec2(1, 0)), f.x),
               mix(ssHash(i + ivec2(0, 1)), ssHash(i + ivec2(1, 1)), f.x), f.y);
}

vec3 ssWind() {
    // RenderSystem shader game time is normalized to 24000 ticks (1200 s).
    // Native time wraps each 1200 s; prototype documents the resulting reset.
    return vec3(0.89442719, 0.0, 0.44721360) * (cloudTime() * 1200.0 * SS_WIND_SPEED);
}

// Adjacent occupied cells meet exactly: broad square islands with stepped tops.
bool ssCell(ivec2 cell, SsCloudLayer layer, out vec3 lo, out vec3 hi) {
    float weather = clamp(volumetricCloudRainBlend(), 0.0, 1.0);
    float coverage = mix(clamp(layer.coverage, 0.0, 1.0), (0.68-0.06*float(layer.id)), weather);
    float field = 0.78 * ssNoise((vec2(cell+layer.seed) + 0.5) / 5.0)
                + 0.22 * ssNoise((vec2(cell+layer.seed) + 17.5) / 2.0);
    float threshold = mix(0.85, 0.15, coverage);
    float tier = floor(clamp((field - threshold) * 8.0, 0.0, 2.999));
    lo = vec3(float(cell.x) * layer.cellSize, layer.base, float(cell.y) * layer.cellSize);
    hi = lo + vec3(layer.cellSize, SS_THICKNESS * (2.0 / 3.0 + tier / 6.0), layer.cellSize);
    // Broad, stable clear areas remove whole groups instead of shrinking every
    // cloud into tiny fragments. This mask is shared by view/light density and
    // uses seeded world cells, so it cannot depend on the camera or DDA cache.
    vec2 groupCell = vec2(cell + layer.seed);
    float groups = 0.8 * ssNoise((groupCell + 91.5) / 32.0)
                 + 0.2 * ssNoise((groupCell - 37.5) / 13.0);
    return groups >= SS8_GROUP_SPACING && field > threshold && coverage > 0.0;
}

// A compact, normalized parabolic kernel filters the UNION of columns.
// Integrating over adjacent equal-height boxes telescopes exactly. This is a
// smooth union of occupied volume, with no individual-cell seam surfaces.
#ifndef SS_EDGE_RADIUS
#define SS_EDGE_RADIUS 1.0
#endif
#ifndef SS_CLOUD_STEP
#define SS_CLOUD_STEP 0.5
#endif
float ssRadius(SsCloudLayer layer) { return min(SS_EDGE_RADIUS, layer.cellSize * 0.2); }
float ssKernelCDF(float x) {
    x = clamp(x, -1.0, 1.0);
    return 0.5 + x * (0.75 - 0.25 * x * x);
}
float ssKernel(float x) { return abs(x) < 1.0 ? 0.75 * (1.0 - x*x) : 0.0; }
vec2 ssInterval(float p, float lo, float hi, float radius) {
    float a = (p-lo)/radius, b = (p-hi)/radius;
    return vec2(ssKernelCDF(a)-ssKernelCDF(b), (ssKernel(a)-ssKernel(b))/radius);
}
struct SsNeighborhood { SsCloudLayer layer; ivec2 cell; float heights[9]; bool occupied; };
SsNeighborhood ssNeighborhood(ivec2 cell,SsCloudLayer layer) {
    SsNeighborhood hood;hood.layer=layer; hood.cell=cell;hood.occupied=false;
    for(int z=-1;z<=1;++z) for(int x=-1;x<=1;++x) {
        vec3 lo,hi;
        hood.heights[(z+1)*3+x+1]=ssCell(cell+ivec2(x,z),layer,lo,hi) ? hi.y : layer.base;
        hood.occupied=hood.occupied||hood.heights[(z+1)*3+x+1]>layer.base;
    }
    return hood;
}
// Returns filtered occupancy and its analytic spatial gradient.
vec4 ssMass(vec3 p, SsNeighborhood hood) {
    vec4 sum=vec4(0.0);float radius=ssRadius(hood.layer);
    // Flat vertical support, shared by every cell in this sheet.
    if(p.y<=hood.layer.base-radius||p.y>=hood.layer.base+SS_THICKNESS+radius)return sum;
    for(int z=-1;z<=1;++z) for(int x=-1;x<=1;++x) {
        float top=hood.heights[(z+1)*3+x+1];
        if(top<=hood.layer.base) continue;
        vec2 corner=vec2(hood.cell+ivec2(x,z))*hood.layer.cellSize;
        vec2 wx=ssInterval(p.x,corner.x,corner.x+hood.layer.cellSize,radius);
        vec2 wz=ssInterval(p.z,corner.y,corner.y+hood.layer.cellSize,radius);
        vec2 wy=ssInterval(p.y,hood.layer.base,top,radius);
        sum+=vec4(wx.x*wy.x*wz.x, wx.y*wy.x*wz.x, wx.x*wy.y*wz.x, wx.x*wy.x*wz.y);
    }
    return sum;
}
float ssDensity(float mass) { return smoothstep(0.06,0.72,clamp(mass,0.0,1.0)); }
bool ssLayer(vec3 ro,vec3 rd,SsCloudLayer layer,out float nearT,out float farT) {
    nearT=0.0;farT=SS_FAR;float radius=ssRadius(layer);
    float bottom=layer.base-radius,top=layer.base+SS_THICKNESS+radius;
    if(abs(rd.y)<1e-7) return ro.y>=bottom&&ro.y<=top;
    float t0=(bottom-ro.y)/rd.y,t1=(top-ro.y)/rd.y;
    nearT=max(min(t0,t1),0.0);farT=min(max(t0,t1),SS_FAR);return farT>nearT;
}
// Explicit integer DDA progression: never recover the next cell by adding
// a sub-ULP world/ray epsilon. That older pattern stalled beyond ~256 blocks.
struct SsDda { ivec2 cell; ivec2 direction; vec2 edgeT; };
float ssEdgeT(vec3 ro,vec3 rd,ivec2 cell,int k,float cellSize) {
    int a=k==0?0:2;
    if(abs(rd[a])<1e-7)return 1e20;
    float edge=(float(cell[k])+(rd[a]>0.0?1.0:0.0))*cellSize;
    return (edge-ro[a])/rd[a];
}
SsDda ssBeginDda(vec3 ro,vec3 rd,float nearT,float cellSize) {
    SsDda d;d.cell=ivec2(floor((ro+rd*nearT).xz/cellSize));
    d.direction=ivec2(sign(rd.xz));
    for(int k=0;k<2;++k) {
        d.edgeT[k]=ssEdgeT(ro,rd,d.cell,k,cellSize);
        // Correct a boundary start on the negative side without discarding
        // a nonzero interval or relying on a floating-point position nudge.
        if(d.edgeT[k]<=nearT&&d.direction[k]!=0) {
            d.cell[k]+=d.direction[k];d.edgeT[k]=ssEdgeT(ro,rd,d.cell,k,cellSize);
        }
    }
    return d;
}
void ssAdvanceDda(inout SsDda d,vec3 ro,vec3 rd,float cellSize) {
    float edge=min(d.edgeT.x,d.edgeT.y);
    for(int k=0;k<2;++k) if(d.edgeT[k]<=edge&&d.direction[k]!=0) {
        d.cell[k]+=d.direction[k];d.edgeT[k]=ssEdgeT(ro,rd,d.cell,k,cellSize);
    }
}
// Ray-global quadrature bins. A bin is owned by the cell containing its
// midpoint, never resized/restarted at a cell boundary. This makes opacity
// and lighting independent of how the same continuous volume is partitioned.
float ssBinCenter(float start,float stepSize,int index,float endT,out float dt) {
    float begin=start+float(index)*stepSize;dt=max(0.0,min(stepSize,endT-begin));
    return begin+0.5*dt;
}
// Front-to-back, disjoint intervals for any camera altitude or ray direction.
struct SsSpan { int layer; float start; float end; float prefix; };
int ssSpans(vec3 ro,vec3 rd,int cellBudget,int sampleBudget,float stepSize,
            out SsSpan spans[3],out float farLimit,out float pathLimit) {
    int count=0;
    for(int i=0;i<3;++i) {
        float a,b;if(!ssLayer(ro,rd,ssCloudLayer(i),a,b)||b<=a)continue;
        SsSpan span;span.layer=i;span.start=a;span.end=b;span.prefix=0.0;
        int insert=count;
        for(int j=0;j<count;++j)if(a<spans[j].start){insert=j;break;}
        for(int j=count;j>insert;--j)spans[j]=spans[j-1];
        spans[insert]=span;++count;
    }
    farLimit=SS_FAR;pathLimit=float(max(sampleBudget-3,1))*stepSize;
    if(count==0)return 0;
    // Each disjoint interval costs at most pathL1/minCell + 3 DDA visits.
    // Reserve nine visits for interval ends; no three independent budgets.
    float minCell=min(SS_CELL_SIZE,min(SS7_MIDDLE_CELL,SS7_UPPER_CELL));
    farLimit=min(SS_FAR,spans[0].start+float(max(cellBudget-9,1))*minCell/max(abs(rd.x)+abs(rd.z),0.001));
    float total=0.0;int kept=0;
    for(int i=0;i<count;++i) {
        spans[i].end=min(spans[i].end,farLimit);
        spans[i].end=min(spans[i].end,spans[i].start+max(pathLimit-total,0.0));
        if(spans[i].end<=spans[i].start)break;
        spans[i].prefix=total;total+=spans[i].end-spans[i].start;++kept;
    }
    return kept;
}
float ssBudgetFade(float distance,float slabDistance,float farLimit,float pathLimit) {
    return (1.0-smoothstep(min(900.0,farLimit*0.75),farLimit,distance))
         * (1.0-smoothstep(pathLimit*0.75,pathLimit,slabDistance));
}
float ssShadow(vec3 absolutePos,vec3 lightDir,int budget) {
    vec3 ro=absolutePos-ssWind();float stepSize=max(SS_EDGE_RADIUS*0.75,0.3);
    int sampleBudget=clamp(budget*8,128,384),cellsUsed=0,samplesUsed=0;
    SsSpan spans[3];float farLimit,pathLimit;
    int count=ssSpans(ro,lightDir,budget,sampleBudget,stepSize,spans,farLimit,pathLimit);
    float depth=0.0;
    for(int layerIndex=0;layerIndex<count;++layerIndex) {
        SsSpan span=spans[layerIndex];SsCloudLayer layer=ssCloudLayer(span.layer);
        SsDda dda=ssBeginDda(ro,lightDir,span.start,layer.cellSize);int index=0;
        while(cellsUsed<budget&&samplesUsed<sampleBudget) {
            ++cellsUsed;float leave=min(min(dda.edgeT.x,dda.edgeT.y),span.end);
            SsNeighborhood hood=ssNeighborhood(dda.cell,layer);
            if(!hood.occupied)index=max(index,int(ceil((leave-span.start)/stepSize-0.5)));
            else while(samplesUsed<sampleBudget) {
                float dt;float center=ssBinCenter(span.start,stepSize,index,span.end,dt);
                if(dt<=0.0||center>=leave)break;
                ++samplesUsed;
                float fade=ssBudgetFade(center,span.prefix+center-span.start,farLimit,pathLimit);
                depth+=ssDensity(ssMass(ro+lightDir*center,hood).x)*dt*max(SS_EXTINCTION,0.0)*fade;
                if(depth>5.5)return exp(-depth);
                ++index;
            }
            if(leave>=span.end)break;
            ssAdvanceDda(dda,ro,lightDir,layer.cellSize);
        }
    }
    return exp(-depth);
}
float volumetricCloudLightVisibility(vec3 absoluteWorldPos,vec3 lightDir,int minimumSteps,float multiScatterExtinction) {
    if(VPT_CLOUD_MODE!=2u||VPT_VOLUMETRIC_CLOUD_CAST_SHADOW==0)return 1.0;
    float v=ssShadow(absoluteWorldPos+lightDir*0.05,lightDir,clamp(minimumSteps*2,16,48));
    return mix(1.0,v,0.42*smoothstep(0.04,0.20,lightDir.y));
}
// Four local Gauss samples reuse the current 3x3 occupancy cache. The entire
// light segment plus filter support fits in that cache, so changing DDA cells
// cannot change this lighting query. No nested DDA/noise queries per view sample.
float ssLocalLight(vec3 p,vec3 lightDir,SsNeighborhood hood) {
    float range=min(8.0,max(0.1,hood.layer.cellSize-2.0*ssRadius(hood.layer)));
    if(lightDir.y>0.001)range=min(range,max(0.0,(hood.layer.base+SS_THICKNESS+ssRadius(hood.layer)-p.y)/lightDir.y));
    const float nodes[4]=float[4](0.0694318442,0.3300094782,0.6699905218,0.9305681558);
    const float weights[4]=float[4](0.1739274226,0.3260725774,0.3260725774,0.1739274226);
    float depth=0.0;
    for(int j=0;j<4;++j)depth+=weights[j]*ssDensity(ssMass(p+lightDir*(nodes[j]*range),hood).x);
    return exp(-depth*range*max(SS_EXTINCTION,0.0));
}
vec3 ssPointLighting(vec3 p,vec3 rd,vec4 mass,SsNeighborhood hood,vec3 lightDir,vec3 ambient,vec3 sunlight,float rain) {
    // A regularized density gradient approaches isotropic lighting continuously
    // inside the volume. There is no camera-facing fallback normal at cell cuts.
    float g2=dot(mass.yzw,mass.yzw);
    vec3 normal=-mass.yzw*inversesqrt(g2+0.0004);
    float face=0.5+0.5*normal.y,ndl=max(dot(normal,lightDir),0.0);
    float visibility=ssLocalLight(p,lightDir,hood);
    float forward=pow(max(dot(rd,lightDir),0.0),12.0);
    float boundary=smoothstep(0.0004,0.0144,g2);
    float grazing=pow(1.0-clamp(abs(dot(normal,rd)),0.0,1.0),2.0)*boundary;
    float rim=SS_SILVER_LINING*visibility*(0.2*grazing+0.8*forward)*mix(1.0,0.15,rain*rain);
    vec3 cool=mix(vec3(0.88,0.94,1.0),vec3(1.0),face);
    return SS_CLOUD_BRIGHTNESS*(ambient*mix(0.78,1.02,face)*cool
        +sunlight*(0.045+0.075*face+visibility*0.50*ndl+rim))*mix(1.0,0.72,rain);
}
VolumetricCloudResult applyVolumetricCloudBudgeted(vec3 rayOrigin,vec3 rayDir,vec3 skyBackgroundRadiance,int viewStepCount,int lightStepCount,int ambientStepCount) {
    VolumetricCloudResult result;result.color=skyBackgroundRadiance;result.transmittance=1.0;result.hit=0.0;
    if(VPT_CLOUD_MODE!=2u)return result;
    vec3 ro=cloudAbsoluteWorldPos(rayOrigin)-ssWind(),rd=cloudSafeNormalize(rayDir,vec3(0,1,0));
    int budget=clamp(viewStepCount*4,32,SS_MAX_CELLS);
    int sampleBudget=clamp(viewStepCount*12,96,SS7_MAX_SAMPLES),cellsUsed=0,samplesUsed=0;
    float stepSize=max(SS_CLOUD_STEP,0.15),farLimit,pathLimit;
    SsSpan spans[3];int count=ssSpans(ro,rd,budget,sampleBudget,stepSize,spans,farLimit,pathLimit);
    if(count==0)return result;
    vec3 lightDir=volumetricCloudPrimaryLightDir();
    vec3 ambient=max(sampleCloudAmbientIrradianceUp(),vec3(0.0));
    vec3 light=max(volumetricCloudPrimaryLightRadiance(),vec3(0.0));
    float rain=clamp(volumetricCloudRainBlend(),0.0,1.0);
    float sunset=(1.0-smoothstep(0.04,0.32,abs(celestialSunDirection().y)))*smoothstep(-0.08,0.06,celestialSunDirection().y)*(1.0-rain);
    vec3 warm=mix(vec3(1.0),vec3(1.15,0.73,0.52),sunset*SS_SUNSET_TINT);
    float sunScale=volumetricCloudSunDirectScale(),moonScale=volumetricCloudMoonDirectScale();
    light*=mix(SS_MOON_GAIN,1.0,sunScale/max(sunScale+moonScale,0.00001));
    ambient*=mix(0.35,1.0,smoothstep(-0.08,0.14,celestialSunDirection().y));
    // One native atmosphere integration for the entire three-sheet query.
    vec3 airSigma=vec3(0.0),airSource=vec3(0.0);
    float airDistance=spans[count-1].end;
    if(SS_AERIAL_STRENGTH>0.0) {
        VolumetricCloudAtmosphereSegmentResult air=integrateAtmosphereSegment(rayOrigin,rd,airDistance);
        vec3 airT=clamp(air.transmittance,vec3(0.00001),vec3(1.0));
        airSigma=-log(airT)/max(airDistance,0.001)*SS_AERIAL_STRENGTH;
        airSource=max(air.scatteredLight,vec3(0.0))/max(vec3(1.0)-airT,vec3(0.00001));
    }
    vec3 scatter=vec3(0.0);
    for(int layerIndex=0;layerIndex<count;++layerIndex) {
        SsSpan span=spans[layerIndex];SsCloudLayer layer=ssCloudLayer(span.layer);
        float radius=clamp(VPT_ATMOSPHERE_RG+layer.base+0.5*SS_THICKNESS,VPT_ATMOSPHERE_RG,VPT_ATMOSPHERE_RT);
        vec3 sunlight=light*sampleCloudAtmosphereTransmittance(radius,lightDir.y)*warm;
        SsDda dda=ssBeginDda(ro,rd,span.start,layer.cellSize);int index=0;
        while(cellsUsed<budget&&samplesUsed<sampleBudget) {
            ++cellsUsed;float leave=min(min(dda.edgeT.x,dda.edgeT.y),span.end);
            SsNeighborhood hood=ssNeighborhood(dda.cell,layer);
            // These indices retain one phase per connected sheet, regardless
            // of DDA boundaries or empty cells. All sheets share both caps.
            if(!hood.occupied)index=max(index,int(ceil((leave-span.start)/stepSize-0.5)));
            else while(samplesUsed<sampleBudget) {
                float dt;float center=ssBinCenter(span.start,stepSize,index,span.end,dt);
                if(dt<=0.0||center>=leave)break;
                ++samplesUsed;
                vec3 p=ro+rd*center;vec4 mass=ssMass(p,hood);
                float alpha=(1.0-exp(-ssDensity(mass.x)*dt*max(SS_EXTINCTION,0.0)))
                    *ssBudgetFade(center,span.prefix+center-span.start,farLimit,pathLimit);
                if(alpha>0.00001) {
                    result.hit=1.0;
                    vec3 color=ssPointLighting(p,rd,mass,hood,lightDir,ambient,sunlight,rain);
                    vec3 airT=exp(-airSigma*center);color=color*airT+airSource*(vec3(1.0)-airT);
                    // Art-directed overcast response uses the actual sky radiance
                    // in this view, preserving day/night and atmospheric hue.
                    // Keep 20% of the lit cloud shape at full rain; no white rims.
                    float overcast=smoothstep(0.05,0.90,rain);
                    color=mix(color,max(skyBackgroundRadiance,vec3(0.0))*0.92,0.80*overcast);
                    scatter+=result.transmittance*alpha*max(color,vec3(0.0));result.transmittance*=1.0-alpha;
                    if(result.transmittance<0.005){result.color=skyBackgroundRadiance*result.transmittance+scatter;return result;}
                }
                ++index;
            }
            if(leave>=span.end)break;
            ssAdvanceDda(dda,ro,rd,layer.cellSize);
        }
    }
    result.color=skyBackgroundRadiance*result.transmittance+scatter;return result;
}
VolumetricCloudResult applyVolumetricCloud(vec3 rayOrigin,vec3 rayDir,vec3 skyBackgroundRadiance) {
    return applyVolumetricCloudBudgeted(rayOrigin,rayDir,skyBackgroundRadiance,VPT_VOLUMETRIC_CLOUD_VIEW_STEPS,VPT_VOLUMETRIC_CLOUD_LIGHT_STEPS,VPT_VOLUMETRIC_CLOUD_AMBIENT_STEPS);
}
#endif
