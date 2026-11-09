#version 150

uniform sampler2D SceneDepth;
uniform sampler3D WorldWater;
uniform mat4 InverseViewProjection;
uniform vec3 CameraInWaterGrid;
uniform vec3 WaterGridOffset;
uniform vec3 WaterFogColor;
uniform int DistanceOnly;
in vec2 texCoord;
out vec4 fragColor;

vec3 valkyrienair_CamRelPos;
#define VS_FLUID_ATLAS_ONLY
#moj_import <vs_fluid_occlusion.glsl>

float lastDryDistance(vec3 direction) {
    float last = 0.0;
    for (int ship = 0; ship < VsFluidOverflowCount; ship++) {
        vec3 minimum = vs_fluidDescriptor(ship, 0).xyz;
        vec3 maximum = vs_fluidDescriptor(ship, 1).xyz;
        float enter = 0.0, exit = 48.0;
        for (int axis = 0; axis < 3; axis++) {
            if (abs(direction[axis]) < 0.0000001) {
                if (minimum[axis] > 0.0 || maximum[axis] < 0.0) exit = -1.0;
            } else {
                float a = minimum[axis] / direction[axis], b = maximum[axis] / direction[axis];
                enter = max(enter, min(a, b)); exit = min(exit, max(a, b));
            }
        }
        if (exit >= enter) last = max(last, exit);
    }
    return last + 0.001;
}

void main() {
    float depth = texture(SceneDepth, texCoord).r;
    vec4 endpoint = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 ray = endpoint.xyz / endpoint.w;
    float distance = min(length(ray), 48.0);
    if (distance < 0.0001) { fragColor = vec4(0.0); return; }
    vec3 direction = normalize(ray);
    float dryLimit = VsFluidOcclusionEnabled < 0.5 ? 0.0 : lastDryDistance(direction);
    ivec3 cell = ivec3(floor(CameraInWaterGrid));
    vec3 stepDirection = sign(direction);
    vec3 reciprocal = 1.0 / max(abs(direction), vec3(0.0000001));
    vec3 nextBoundary = (vec3(cell) + max(stepDirection, vec3(0.0)) - CameraInWaterGrid) *
        stepDirection * reciprocal;
    if (abs(direction.x) < 0.0000001) nextBoundary.x = 1e30;
    if (abs(direction.y) < 0.0000001) nextBoundary.y = 1e30;
    if (abs(direction.z) < 0.0000001) nextBoundary.z = 1e30;
    float start = 0.0;
    float wetDistance = 0.0;
    for (int iteration = 0; iteration < 148 && start < distance; iteration++) {
        if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, textureSize(WorldWater, 0)))) break;
        float end = min(distance, min(nextBoundary.x, min(nextBoundary.y, nextBoundary.z)));
        float height = texelFetch(WorldWater, (cell + ivec3(WaterGridOffset)) % textureSize(WorldWater, 0), 0).r;
        float wetStart = start, wetEnd = end;
        if (height > 0.0) {
            float surface = float(cell.y) + height;
            if (abs(direction.y) < 0.0000001) {
                if (CameraInWaterGrid.y >= surface) wetEnd = wetStart;
            } else {
                float crossing = (surface - CameraInWaterGrid.y) / direction.y;
                if (direction.y > 0.0) wetEnd = min(wetEnd, crossing);
                else wetStart = max(wetStart, crossing);
            }
            float span = max(0.0, wetEnd - wetStart);
            if (wetStart > dryLimit) wetDistance += span;
            else for (int sampleIndex = 0; sampleIndex < 4 && span > 0.0; sampleIndex++) {
                valkyrienair_CamRelPos = direction * (wetStart + span * (float(sampleIndex) + 0.5) * 0.25);
                if (VsFluidOcclusionEnabled < 0.5 || !vs_shouldCullOverflow(VsFluidOverflowDescriptors)) {
                    wetDistance += span * 0.25;
                }
            }
        }
        if (nextBoundary.x <= nextBoundary.y && nextBoundary.x <= nextBoundary.z) {
            cell.x += int(stepDirection.x); nextBoundary.x += reciprocal.x;
        } else if (nextBoundary.y <= nextBoundary.z) {
            cell.y += int(stepDirection.y); nextBoundary.y += reciprocal.y;
        } else {
            cell.z += int(stepDirection.z); nextBoundary.z += reciprocal.z;
        }
        start = end;
    }
    fragColor = DistanceOnly != 0 ? vec4(wetDistance, 0.0, 0.0, 1.0)
        : vec4(WaterFogColor, 1.0 - exp(-wetDistance * 0.075));
}
