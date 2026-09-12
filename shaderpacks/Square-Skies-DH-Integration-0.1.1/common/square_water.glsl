// SPDX-License-Identifier: GPL-3.0-only
// Original Square Skies water helpers, 2026-09-05. No Complementary source.
#ifndef SS_WATER_GLSL
#define SS_WATER_GLSL
#ifndef SS3_WATER_RIPPLE_STRENGTH
#define SS3_WATER_RIPPLE_STRENGTH 0.65
#endif
#ifndef SS3_DEBUG_VIEW
#define SS3_DEBUG_VIEW 0
#endif
#ifndef SS_WATER_ENABLE
#define SS_WATER_ENABLE 1
#endif
#ifndef SS_WATER_SHAFTS
#define SS_WATER_SHAFTS 1
#endif
#ifndef SS_WATER_STEPS
#define SS_WATER_STEPS 6
#endif
#ifndef SS_WATER_DENSITY
#define SS_WATER_DENSITY 0.6
#endif
#ifndef SS_WATER_SHAFT_STRENGTH
#define SS_WATER_SHAFT_STRENGTH 1.0
#endif
#ifndef SS_WATER_CAUSTICS
#define SS_WATER_CAUSTICS 0.65
#endif
#ifndef SS_WATER_DISTANCE
#define SS_WATER_DISTANCE 64.0
#endif

vec3 ssWaterExtinction() {
    // Per-block extinction; preserves nearby warm colors, absorbs red with depth.
    return vec3(0.12, 0.043, 0.025) * max(SS_WATER_DENSITY, 0.05);
}

#ifndef SS5_WATER_FINE_DETAIL
#define SS5_WATER_FINE_DETAIL 0.8
#endif
#ifndef SS5_WATER_FILTER
#define SS5_WATER_FILTER 1.0
#endif
struct SsWaterWave { float height; vec2 slope; mat2 hessian; float unresolvedVariance; };
void ssAddWaterWave(vec2 p,float t,vec2 d,float k,float a,float speed,float footprint,inout SsWaterWave w) {
    float phase=dot(p,d)*k+t*speed;
    float attenuation=exp(-0.5*pow(k*footprint*SS5_WATER_FILTER,2.0));
    float amplitude=a*SS3_WATER_RIPPLE_STRENGTH;
    w.unresolvedVariance+=0.5*pow(amplitude*k,2.0)*(1.0-attenuation*attenuation);
    amplitude*=attenuation;
    w.height+=amplitude*sin(phase);
    w.slope+=d*(amplitude*k*cos(phase));
    w.hessian+=outerProduct(d,d)*(-amplitude*k*k*sin(phase));
}
SsWaterWave ssWaterWave(vec2 p,float normalizedTime,float footprint) {
    SsWaterWave w;w.height=0.0;w.slope=vec2(0.0);w.hessian=mat2(0.0);w.unresolvedVariance=0.0;
    float t=normalizedTime*1200.0;
    ssAddWaterWave(p,t,normalize(vec2(1.0,0.3)),0.75,0.045,0.82,footprint,w);
    ssAddWaterWave(p,t,normalize(vec2(-0.4,1.0)),1.6,0.025,-1.17,footprint,w);
    ssAddWaterWave(p,t,normalize(vec2(0.65,-0.76)),3.3,0.012,1.58,footprint,w);
    ssAddWaterWave(p,t,normalize(vec2(-0.91,-0.42)),6.8,0.010*SS5_WATER_FINE_DETAIL,-2.08,footprint,w);
    ssAddWaterWave(p,t,normalize(vec2(0.24,0.97)),12.0,0.0035*SS5_WATER_FINE_DETAIL,2.72,footprint,w);
    ssAddWaterWave(p,t,normalize(vec2(0.87,-0.49)),20.0,0.0012*SS5_WATER_FINE_DETAIL,-3.19,footprint,w);
    return w;
}
vec3 ssRippleNormal(vec2 p,float normalizedTime) {
    SsWaterWave w=ssWaterWave(p,normalizedTime,0.0);
    return normalize(vec3(-w.slope.x,1.0,-w.slope.y));
}
// Receiver map and analytic Jacobian of Snell refraction. The height field is
// shared with surface shading; geometry remains the native planar water mesh.
void ssWaterReceiverMap(vec2 q,float time,vec3 incoming,float depth,float footprint,out vec2 receiver,out mat2 jacobian) {
    SsWaterWave w=ssWaterWave(q,time,footprint);
    vec3 raw=vec3(-w.slope.x,1.0,-w.slope.y);float len=length(raw);vec3 n=raw/len;
    const float eta=1.0/1.333;
    float c=dot(n,incoming),root=sqrt(max(1.0-eta*eta*(1.0-c*c),0.001));
    float b=eta*c+root;vec3 bent=eta*incoming-b*n;
    float vertical=max(-bent.y,0.15),d=max(depth+w.height,0.01);
    float heightDerivative=depth+w.height>0.01?1.0:0.0;
    receiver=q+bent.xz*d/vertical;jacobian=mat2(1.0);
    for(int axis=0;axis<2;++axis) {
        vec2 curvature=w.hessian[axis];vec3 dr=vec3(-curvature.x,0.0,-curvature.y);
        vec3 dn=(dr-n*dot(n,dr))/len;float dc=dot(dn,incoming);
        vec3 dbent=-(eta+eta*eta*c/root)*dc*n-b*dn;
        vec2 derivative=d*(dbent.xz*vertical+bent.xz*dbent.y)/(vertical*vertical)
                       +bent.xz*w.slope[axis]*heightDerivative/vertical;
        jacobian[axis]+=derivative;
    }
}
float ssWaterCausticReceiver(vec2 receiverXZ,float time,vec3 toSun,float depth,float footprint) {
    if(SS_WATER_CAUSTICS<=0.0||toSun.y<=0.03||depth<=0.01)return 1.0;
    vec3 incoming=-normalize(toSun),flatRay=refract(incoming,vec3(0,1,0),1.0/1.333);
    float d=min(depth,24.0);
    // Footprint includes the unresolved finite solar disk/depth spread, so
    // far/deep fine ripples do not produce singular sparkling caustics.
    float filterWidth=max(footprint,0.035+d*0.01);
    vec2 q=receiverXZ-flatRay.xz*d/max(-flatRay.y,0.15),mapped;mat2 j;
    for(int i=0;i<4;++i) {
        ssWaterReceiverMap(q,time,incoming,d,filterWidth,mapped,j);
        vec2 residual=mapped-receiverXZ;
        // Damped least squares remains finite through folds.
        mat2 jt=transpose(j),normal=jt*j+mat2(0.035);
        vec2 step=inverse(normal)*(jt*residual);
        q-=step*min(1.0,0.7/max(length(step),0.0001));
    }
    ssWaterReceiverMap(q,time,incoming,d,filterWidth,mapped,j);
    float residual=length(mapped-receiverXZ);
    float area=abs(determinant(j));
    float focus=clamp(1.1/(area+0.1),0.45,2.6);
    float confidence=exp(-pow(residual/max(filterWidth,0.08),2.0));
    float fade=(1.0-exp(-d*1.25))*exp(-depth*0.055)*smoothstep(0.03,0.3,toSun.y);
    return mix(1.0,focus,clamp(SS_WATER_CAUSTICS,0.0,1.0)*fade*confidence);
}
float ssWaterInterfaceTransmission(vec3 toSun) {
    float f0=pow((1.333-1.0)/(1.333+1.0),2.0);
    return 1.0-(f0+(1.0-f0)*pow(1.0-clamp(toSun.y,0.0,1.0),5.0));
}
#endif
