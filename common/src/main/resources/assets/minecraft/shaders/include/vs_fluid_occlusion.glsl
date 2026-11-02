uniform float VsFluidOcclusionEnabled;
#ifndef VS_FLUID_ATLAS_ONLY
uniform int VsFluidOcclusionPartialCount0;
uniform int VsFluidOcclusionPartialCount1;
uniform int VsFluidOcclusionPartialCount2;
uniform int VsFluidOcclusionPartialCount3;
uniform int VsFluidOcclusionPartialCount4;
uniform int VsFluidOcclusionPartialCount5;
uniform int VsFluidOcclusionPartialCount6;
uniform int VsFluidOcclusionPartialCount7;
uniform int VsFluidOcclusionPartialCount8;
#endif
uniform vec4 VsFluidOcclusionUv0;
uniform vec4 VsFluidOcclusionUv1;
uniform vec4 VsFluidOcclusionUv2;
uniform vec4 VsFluidOcclusionUv3;
uniform vec4 VsFluidOcclusionUv4;

#ifndef VS_FLUID_ATLAS_ONLY
uniform sampler2D VsFluidOcclusionMask0;
uniform sampler2D VsFluidOcclusionMask1;
uniform sampler2D VsFluidOcclusionMask2;
uniform sampler2D VsFluidOcclusionMask3;
uniform sampler2D VsFluidOcclusionMask4;
uniform sampler2D VsFluidOcclusionMask5;
uniform sampler2D VsFluidOcclusionMask6;
uniform sampler2D VsFluidOcclusionMask7;
uniform sampler2D VsFluidOcclusionMask8;

uniform vec3 VsFluidOcclusionGridSize0;
uniform vec3 VsFluidOcclusionGridSize1;
uniform vec3 VsFluidOcclusionGridSize2;
uniform vec3 VsFluidOcclusionGridSize3;
uniform vec3 VsFluidOcclusionGridSize4;
uniform vec3 VsFluidOcclusionGridSize5;
uniform vec3 VsFluidOcclusionGridSize6;
uniform vec3 VsFluidOcclusionGridSize7;
uniform vec3 VsFluidOcclusionGridSize8;

uniform vec3 VsFluidOcclusionCameraLocal0;
uniform vec3 VsFluidOcclusionCameraLocal1;
uniform vec3 VsFluidOcclusionCameraLocal2;
uniform vec3 VsFluidOcclusionCameraLocal3;
uniform vec3 VsFluidOcclusionCameraLocal4;
uniform vec3 VsFluidOcclusionCameraLocal5;
uniform vec3 VsFluidOcclusionCameraLocal6;
uniform vec3 VsFluidOcclusionCameraLocal7;
uniform vec3 VsFluidOcclusionCameraLocal8;

uniform mat4 VsFluidOcclusionWorldToShip0;
uniform mat4 VsFluidOcclusionWorldToShip1;
uniform mat4 VsFluidOcclusionWorldToShip2;
uniform mat4 VsFluidOcclusionWorldToShip3;
uniform mat4 VsFluidOcclusionWorldToShip4;
uniform mat4 VsFluidOcclusionWorldToShip5;
uniform mat4 VsFluidOcclusionWorldToShip6;
uniform mat4 VsFluidOcclusionWorldToShip7;
uniform mat4 VsFluidOcclusionWorldToShip8;

#endif

const int VS_FLUID_MASK_WIDTH_SHIFT = 12;
const int VS_FLUID_MASK_WIDTH_MASK = 4095;
const float VS_FLUID_SAMPLE_EPSILON = 0.0001;
uniform sampler2DArray VsFluidOverflowMasks;
uniform sampler2D VsFluidOverflowDescriptors;
uniform int VsFluidOverflowCount;

bool vs_fluidUvWithin(vec2 uv, vec4 bounds) {
    return uv.x >= bounds.x && uv.x <= bounds.z &&
        uv.y >= bounds.y && uv.y <= bounds.w;
}

bool vs_isFluidSprite(vec2 uv) {
    return vs_fluidUvWithin(uv, VsFluidOcclusionUv0) ||
        vs_fluidUvWithin(uv, VsFluidOcclusionUv1) ||
        vs_fluidUvWithin(uv, VsFluidOcclusionUv2) ||
        vs_fluidUvWithin(uv, VsFluidOcclusionUv3) ||
        vs_fluidUvWithin(uv, VsFluidOcclusionUv4);
}

uint vs_fetchFluidMaskWord(sampler2D mask, int wordIndex) {
    ivec2 coord = ivec2(
        wordIndex & VS_FLUID_MASK_WIDTH_MASK,
        wordIndex >> VS_FLUID_MASK_WIDTH_SHIFT
    );
    uvec4 bytes = uvec4(round(texelFetch(mask, coord, 0) * 255.0));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

uint vs_fetchFluidWord(sampler2D mask, int wordIndex, int atlasOffset) {
    if (atlasOffset < 0) return vs_fetchFluidMaskWord(mask, wordIndex);
    ivec3 size = textureSize(VsFluidOverflowMasks, 0);
    int index = atlasOffset + wordIndex;
    int row = index / size.x;
    uvec4 bytes = uvec4(round(texelFetch(VsFluidOverflowMasks,
        ivec3(index % size.x, row % size.y, row / size.y), 0) * 255.0));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

bool vs_shouldCullFluidAtOffset(
    sampler2D mask,
    vec3 gridSize,
    vec3 cameraLocal,
    mat4 worldToShip,
    int partialCount,
    int atlasOffset
) {
    if (gridSize.x <= 0.0) return false;
    vec3 cameraRelative = valkyrienair_CamRelPos +
        vec3(0.0, -VS_FLUID_SAMPLE_EPSILON, 0.0);
    vec3 localPos = (worldToShip * vec4(cameraRelative, 0.0)).xyz +
        cameraLocal;
    if (any(lessThan(localPos, vec3(0.0))) ||
        any(greaterThanEqual(localPos, gridSize))) {
        return false;
    }
    ivec3 voxel = ivec3(floor(localPos));
    ivec3 size = ivec3(gridSize);
    int voxelIndex = voxel.x + size.x * (voxel.y + size.y * voxel.z);
    // Wholly dry masks keep their original one-bit layout and single fetch.
    if (partialCount == 0) {
        uint word = vs_fetchFluidWord(mask, voxelIndex >> 5, atlasOffset);
        return ((word >> uint(voxelIndex & 31)) & 1u) != 0u;
    }
    // Mixed masks use two-bit states, followed by sorted (24-bit index, 8-bit fill) records.
    uint word = vs_fetchFluidWord(mask, voxelIndex >> 4, atlasOffset);
    uint state = (word >> uint((voxelIndex & 15) * 2)) & 3u;
    if (state != 2u) return state == 1u;
    int denseWords = (size.x * size.y * size.z + 15) >> 4;
    int low = 0;
    int high = partialCount - 1;
    while (low <= high) {
        int middle = low + ((high - low) >> 1);
        uint record = vs_fetchFluidWord(mask, denseWords + middle, atlasOffset);
        uint index = record >> 8u;
        if (index == uint(voxelIndex)) {
            return fract(localPos.y) > float(record & 255u) / 255.0;
        }
        if (index < uint(voxelIndex)) low = middle + 1;
        else high = middle - 1;
    }
    return false;
}

bool vs_shouldCullFluid(sampler2D mask, vec3 gridSize, vec3 cameraLocal, mat4 worldToShip, int partialCount) {
    return vs_shouldCullFluidAtOffset(mask, gridSize, cameraLocal, worldToShip, partialCount, -1);
}

vec4 vs_fluidDescriptor(int ship, int field) {
    int width = textureSize(VsFluidOverflowDescriptors, 0).x;
    int index = ship * 8 + field;
    return texelFetch(VsFluidOverflowDescriptors, ivec2(index % width, index / width), 0);
}

bool vs_shouldCullOverflow(sampler2D directMask) {
    vec3 point = valkyrienair_CamRelPos + vec3(0.0, -VS_FLUID_SAMPLE_EPSILON, 0.0);
    for (int ship = 0; ship < VsFluidOverflowCount; ship++) {
        vec4 minimum = vs_fluidDescriptor(ship, 0);
        vec4 maximum = vs_fluidDescriptor(ship, 1);
        if (any(lessThan(point, minimum.xyz)) || any(greaterThanEqual(point, maximum.xyz))) continue;
        vec4 grid = vs_fluidDescriptor(ship, 2);
        mat4 toShip = mat4(vec4(vs_fluidDescriptor(ship, 4).xyz, 0.0),
            vec4(vs_fluidDescriptor(ship, 5).xyz, 0.0), vec4(vs_fluidDescriptor(ship, 6).xyz, 0.0),
            vec4(0.0, 0.0, 0.0, 1.0));
        int offset = int(minimum.w) + int(maximum.w) * 65536;
        if (vs_shouldCullFluidAtOffset(directMask, grid.xyz, vs_fluidDescriptor(ship, 3).xyz,
            toShip, int(grid.w), offset)) return true;
    }
    return false;
}
