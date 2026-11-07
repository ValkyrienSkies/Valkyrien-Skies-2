#version 150

uniform sampler2D SceneDepthSampler;
uniform mat4 InverseProjMat;
uniform mat4 InverseViewMat;
uniform vec3 CameraWorldPos;
uniform vec3 CameraLookVector;
uniform vec3 CameraUpVector;
uniform vec3 CameraLeftVector;
uniform int ValkyrienAir_ShipCount;
uniform vec4 ValkyrienAir_ShipAabbMin0;
uniform vec4 ValkyrienAir_ShipAabbMax0;
uniform vec4 ValkyrienAir_GridSize0;
uniform mat4 ValkyrienAir_WorldToShip0;
uniform sampler2D ValkyrienAir_Mask0;
uniform vec4 ValkyrienAir_ShipAabbMin1;
uniform vec4 ValkyrienAir_ShipAabbMax1;
uniform vec4 ValkyrienAir_GridSize1;
uniform mat4 ValkyrienAir_WorldToShip1;
uniform sampler2D ValkyrienAir_Mask1;
uniform vec4 ValkyrienAir_ShipAabbMin2;
uniform vec4 ValkyrienAir_ShipAabbMax2;
uniform vec4 ValkyrienAir_GridSize2;
uniform mat4 ValkyrienAir_WorldToShip2;
uniform sampler2D ValkyrienAir_Mask2;
uniform vec4 ValkyrienAir_ShipAabbMin3;
uniform vec4 ValkyrienAir_ShipAabbMax3;
uniform vec4 ValkyrienAir_GridSize3;
uniform mat4 ValkyrienAir_WorldToShip3;
uniform sampler2D ValkyrienAir_Mask3;
uniform vec4 ValkyrienAir_ShipAabbMin4;
uniform vec4 ValkyrienAir_ShipAabbMax4;
uniform vec4 ValkyrienAir_GridSize4;
uniform mat4 ValkyrienAir_WorldToShip4;
uniform sampler2D ValkyrienAir_Mask4;
uniform vec4 ValkyrienAir_ShipAabbMin5;
uniform vec4 ValkyrienAir_ShipAabbMax5;
uniform vec4 ValkyrienAir_GridSize5;
uniform mat4 ValkyrienAir_WorldToShip5;
uniform sampler2D ValkyrienAir_Mask5;
uniform vec4 ValkyrienAir_ShipAabbMin6;
uniform vec4 ValkyrienAir_ShipAabbMax6;
uniform vec4 ValkyrienAir_GridSize6;
uniform mat4 ValkyrienAir_WorldToShip6;
uniform sampler2D ValkyrienAir_Mask6;
uniform vec4 ValkyrienAir_ShipAabbMin7;
uniform vec4 ValkyrienAir_ShipAabbMax7;
uniform vec4 ValkyrienAir_GridSize7;
uniform mat4 ValkyrienAir_WorldToShip7;
uniform sampler2D ValkyrienAir_Mask7;
uniform vec4 ValkyrienAir_ShipAabbMin8;
uniform vec4 ValkyrienAir_ShipAabbMax8;
uniform vec4 ValkyrienAir_GridSize8;
uniform mat4 ValkyrienAir_WorldToShip8;
uniform sampler2D ValkyrienAir_Mask8;

in vec2 texCoord;
out vec4 fragColor;

const int VA_MASK_TEX_WIDTH_SHIFT = 12;
const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;
const int VA_OCC_WORDS_PER_VOXEL = 16;
const int VA_MAX_RAY_STEPS = 64;
const int VA_EXIT_NEIGHBOR_RADIUS = 2;

vec3 reconstructWorldPos(float depth) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos4 = InverseProjMat * clipPos;
    vec3 viewPos = viewPos4.xyz / viewPos4.w;
    return CameraWorldPos
        - CameraLeftVector * viewPos.x
        + CameraUpVector * viewPos.y
        - CameraLookVector * viewPos.z;
}

uint va_fetchWord(sampler2D tex, int wordIndex) {
    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);
    vec4 raw = texelFetch(tex, coord, 0) * 255.0;
    uvec4 bytes = uvec4(round(raw));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

bool va_testAir(sampler2D mask, int voxelIdx, ivec3 isize) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int wordIndex = occBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_testWaterReachable(sampler2D mask, int voxelIdx, ivec3 isize) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int airWordCount = (volume + 31) >> 5;
    int waterBase = occBase + airWordCount + airWordCount;
    int wordIndex = waterBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_insideShip(vec3 worldPos, vec4 aabbMin, vec4 aabbMax, vec4 gridSize, mat4 worldToShip, sampler2D mask) {
    if (gridSize.x <= 0.0) return false;
    if (worldPos.x < aabbMin.x || worldPos.x > aabbMax.x) return false;
    if (worldPos.y < aabbMin.y || worldPos.y > aabbMax.y) return false;
    if (worldPos.z < aabbMin.z || worldPos.z > aabbMax.z) return false;

    vec3 shipPos = (worldToShip * vec4(worldPos, 1.0)).xyz;
    if (shipPos.x < 0.0 || shipPos.y < 0.0 || shipPos.z < 0.0) return false;
    if (shipPos.x >= gridSize.x || shipPos.y >= gridSize.y || shipPos.z >= gridSize.z) return false;

    ivec3 isize = ivec3(gridSize.xyz);
    ivec3 voxel = ivec3(floor(shipPos));
    int voxelIdx = voxel.x + isize.x * (voxel.y + isize.y * voxel.z);
    return va_testAir(mask, voxelIdx, isize);
}

bool va_rayAabbSegment(vec3 rayOrigin, vec3 rayDir, float maxDistance, vec3 aabbMin, vec3 aabbMax, out float tEnter, out float tExit) {
    vec3 invDir = 1.0 / max(abs(rayDir), vec3(1.0e-6)) * sign(rayDir);
    vec3 t0 = (aabbMin - rayOrigin) * invDir;
    vec3 t1 = (aabbMax - rayOrigin) * invDir;
    vec3 tMin = min(t0, t1);
    vec3 tMax = max(t0, t1);

    tEnter = max(max(tMin.x, tMin.y), max(tMin.z, 0.0));
    tExit = min(min(tMax.x, tMax.y), min(tMax.z, maxDistance));
    return tExit >= tEnter;
}

float va_initialDryDistance(vec3 rayStart, vec3 rayEnd, vec4 aabbMin, vec4 aabbMax, vec4 gridSize, mat4 worldToShip, sampler2D mask) {
    if (gridSize.x <= 0.0) return 0.0;

    vec3 rayVec = rayEnd - rayStart;
    float rayLength = length(rayVec);
    if (rayLength <= 1.0e-4) {
        return va_insideShip(rayEnd, aabbMin, aabbMax, gridSize, worldToShip, mask) ? rayLength : 0.0;
    }

    vec3 rayDir = rayVec / rayLength;
    if (!va_insideShip(rayStart, aabbMin, aabbMax, gridSize, worldToShip, mask)) {
        return 0.0;
    }

    float tEnter;
    float tExit;
    if (!va_rayAabbSegment(rayStart, rayDir, rayLength, aabbMin.xyz, aabbMax.xyz, tEnter, tExit)) {
        return 0.0;
    }

    float stepLength = max(0.25, tExit / float(VA_MAX_RAY_STEPS));
    float travel = 0.0;
    float lastInside = 0.0;
    for (int i = 0; i < VA_MAX_RAY_STEPS; i++) {
        if (travel > tExit) break;
        vec3 samplePos = rayStart + rayDir * travel;
        if (!va_insideShip(samplePos, aabbMin, aabbMax, gridSize, worldToShip, mask)) {
            return lastInside;
        }
        lastInside = travel;
        travel += stepLength;
    }

    return tExit;
}

float va_waterReachableNearDryExit(vec3 rayStart, vec3 rayEnd, float dryDistance, vec4 aabbMin, vec4 aabbMax, vec4 gridSize, mat4 worldToShip, sampler2D mask) {
    if (gridSize.x <= 0.0) return 0.0;

    vec3 rayVec = rayEnd - rayStart;
    float rayLength = length(rayVec);
    if (rayLength <= 1.0e-4 || dryDistance <= 0.0) return 0.0;

    vec3 rayDir = rayVec / rayLength;
    float probeDistance = clamp(max(0.0, dryDistance - 0.1), 0.0, rayLength);
    vec3 probePos = rayStart + rayDir * probeDistance;
    vec3 shipPos = (worldToShip * vec4(probePos, 1.0)).xyz;
    ivec3 isize = ivec3(gridSize.xyz);

    if (shipPos.x < 0.0 || shipPos.y < 0.0 || shipPos.z < 0.0) return 0.0;
    if (shipPos.x >= gridSize.x || shipPos.y >= gridSize.y || shipPos.z >= gridSize.z) return 0.0;

    ivec3 centerVoxel = ivec3(floor(shipPos));
    for (int dz = -VA_EXIT_NEIGHBOR_RADIUS; dz <= VA_EXIT_NEIGHBOR_RADIUS; dz++) {
        for (int dy = -VA_EXIT_NEIGHBOR_RADIUS; dy <= VA_EXIT_NEIGHBOR_RADIUS; dy++) {
            for (int dx = -VA_EXIT_NEIGHBOR_RADIUS; dx <= VA_EXIT_NEIGHBOR_RADIUS; dx++) {
                ivec3 voxel = centerVoxel + ivec3(dx, dy, dz);
                if (voxel.x < 0 || voxel.y < 0 || voxel.z < 0) continue;
                if (voxel.x >= isize.x || voxel.y >= isize.y || voxel.z >= isize.z) continue;
                int voxelIdx = voxel.x + isize.x * (voxel.y + isize.y * voxel.z);
                if (va_testWaterReachable(mask, voxelIdx, isize)) {
                    return 1.0;
                }
            }
        }
    }

    return 0.0;
}

void main() {
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    vec3 cameraWorldPos = CameraWorldPos;
    float depthForTrace = min(sceneDepth, 0.99999);
    vec3 sceneWorldPos = reconstructWorldPos(depthForTrace);
    float sceneDistance = length(sceneWorldPos - cameraWorldPos);
    if (sceneDistance <= 1.0e-4) {
        fragColor = vec4(0.0);
        return;
    }

    float dryDistance = 0.0;
    float waterVisible = 0.0;
    if (ValkyrienAir_ShipCount > 0) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin0, ValkyrienAir_ShipAabbMax0, ValkyrienAir_GridSize0, ValkyrienAir_WorldToShip0, ValkyrienAir_Mask0));
    if (ValkyrienAir_ShipCount > 1) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin1, ValkyrienAir_ShipAabbMax1, ValkyrienAir_GridSize1, ValkyrienAir_WorldToShip1, ValkyrienAir_Mask1));
    if (ValkyrienAir_ShipCount > 2) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin2, ValkyrienAir_ShipAabbMax2, ValkyrienAir_GridSize2, ValkyrienAir_WorldToShip2, ValkyrienAir_Mask2));
    if (ValkyrienAir_ShipCount > 3) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin3, ValkyrienAir_ShipAabbMax3, ValkyrienAir_GridSize3, ValkyrienAir_WorldToShip3, ValkyrienAir_Mask3));
    if (ValkyrienAir_ShipCount > 4) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin4, ValkyrienAir_ShipAabbMax4, ValkyrienAir_GridSize4, ValkyrienAir_WorldToShip4, ValkyrienAir_Mask4));
    if (ValkyrienAir_ShipCount > 5) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin5, ValkyrienAir_ShipAabbMax5, ValkyrienAir_GridSize5, ValkyrienAir_WorldToShip5, ValkyrienAir_Mask5));
    if (ValkyrienAir_ShipCount > 6) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin6, ValkyrienAir_ShipAabbMax6, ValkyrienAir_GridSize6, ValkyrienAir_WorldToShip6, ValkyrienAir_Mask6));
    if (ValkyrienAir_ShipCount > 7) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin7, ValkyrienAir_ShipAabbMax7, ValkyrienAir_GridSize7, ValkyrienAir_WorldToShip7, ValkyrienAir_Mask7));
    if (ValkyrienAir_ShipCount > 8) dryDistance = max(dryDistance, va_initialDryDistance(cameraWorldPos, sceneWorldPos, ValkyrienAir_ShipAabbMin8, ValkyrienAir_ShipAabbMax8, ValkyrienAir_GridSize8, ValkyrienAir_WorldToShip8, ValkyrienAir_Mask8));

    if (ValkyrienAir_ShipCount > 0) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin0, ValkyrienAir_ShipAabbMax0, ValkyrienAir_GridSize0, ValkyrienAir_WorldToShip0, ValkyrienAir_Mask0));
    if (ValkyrienAir_ShipCount > 1) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin1, ValkyrienAir_ShipAabbMax1, ValkyrienAir_GridSize1, ValkyrienAir_WorldToShip1, ValkyrienAir_Mask1));
    if (ValkyrienAir_ShipCount > 2) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin2, ValkyrienAir_ShipAabbMax2, ValkyrienAir_GridSize2, ValkyrienAir_WorldToShip2, ValkyrienAir_Mask2));
    if (ValkyrienAir_ShipCount > 3) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin3, ValkyrienAir_ShipAabbMax3, ValkyrienAir_GridSize3, ValkyrienAir_WorldToShip3, ValkyrienAir_Mask3));
    if (ValkyrienAir_ShipCount > 4) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin4, ValkyrienAir_ShipAabbMax4, ValkyrienAir_GridSize4, ValkyrienAir_WorldToShip4, ValkyrienAir_Mask4));
    if (ValkyrienAir_ShipCount > 5) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin5, ValkyrienAir_ShipAabbMax5, ValkyrienAir_GridSize5, ValkyrienAir_WorldToShip5, ValkyrienAir_Mask5));
    if (ValkyrienAir_ShipCount > 6) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin6, ValkyrienAir_ShipAabbMax6, ValkyrienAir_GridSize6, ValkyrienAir_WorldToShip6, ValkyrienAir_Mask6));
    if (ValkyrienAir_ShipCount > 7) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin7, ValkyrienAir_ShipAabbMax7, ValkyrienAir_GridSize7, ValkyrienAir_WorldToShip7, ValkyrienAir_Mask7));
    if (ValkyrienAir_ShipCount > 8) waterVisible = max(waterVisible, va_waterReachableNearDryExit(cameraWorldPos, sceneWorldPos, dryDistance, ValkyrienAir_ShipAabbMin8, ValkyrienAir_ShipAabbMax8, ValkyrienAir_GridSize8, ValkyrienAir_WorldToShip8, ValkyrienAir_Mask8));

    float dryFraction = clamp(dryDistance / sceneDistance, 0.0, 1.0);
    fragColor = vec4(dryFraction, waterVisible, 0.0, 1.0);
}
