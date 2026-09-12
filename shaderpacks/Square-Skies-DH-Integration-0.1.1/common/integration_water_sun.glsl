#ifndef INTEGRATION_WATER_SUN_GLSL
#define INTEGRATION_WATER_SUN_GLSL
#include "common/square_water.glsl"
vec3 ssSharedReceiverSun(vec3 receiverRelative,vec3 cameraPos,vec3 shadowDirection,
                         float hitDistance,float gameTime,vec3 sun,out float caustic) {
    vec3 receiver=receiverRelative+cameraPos;
    float depth=hitDistance*shadowDirection.y;
    float footprint=max(0.03,length(receiverRelative)*0.0015);
    caustic=ssWaterCausticReceiver(receiver.xz,gameTime,sun,depth,footprint);
    return exp(-ssWaterExtinction()*max(hitDistance,0.0))*
           ssWaterInterfaceTransmission(sun.y>0.0?sun:-sun)*caustic;
}
#endif
