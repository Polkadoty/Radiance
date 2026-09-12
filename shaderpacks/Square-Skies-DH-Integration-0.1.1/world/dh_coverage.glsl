// Published from the same completed normal chunk set used to build this frame's TLAS.
layout(set = 1, binding = 10, std430) readonly buffer RadianceDhNearCoverage { ivec4 dhNearCells[]; };
int radianceDhWrap(int value, int size) {
    // GLSL signed remainder is not defined for negative operands. Compute a
    // positive magnitude in unsigned arithmetic (including INT_MIN), then wrap.
    uint magnitude = value < 0 ? 0u - uint(value) : uint(value);
    uint remainder = magnitude % uint(size);
    return value < 0 && remainder != 0u ? size - int(remainder) : int(remainder);
}
bool radianceDhInsideNearCoverage() {
    ivec4 grid=dhNearCells[0];
    if (grid.x<=0 || grid.y<=0 || grid.z<=0) return false;
    dvec3 worldHit=ubo.cameraPos.xyz + dvec3(gl_WorldRayOriginEXT + (gl_HitTEXT+0.002) * gl_WorldRayDirectionEXT);
    ivec3 section=ivec3(floor(worldHit/16.0));
    if (section.y<grid.w || section.y>=grid.w+grid.y) return false;
    int index=1+radianceDhWrap(section.x,grid.x)+radianceDhWrap(section.z,grid.z)*grid.x+(section.y-grid.w)*grid.x*grid.z;
    return all(equal(dhNearCells[index],ivec4(section,1)));
}
