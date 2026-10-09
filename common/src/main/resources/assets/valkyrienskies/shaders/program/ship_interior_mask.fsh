#version 150

uniform sampler2D SceneDepthSampler;
uniform sampler2D ValkyrienAir_MaskAtlas;
// One row per ship, 7 RGBA32F texels wide:
//   col 0 = (aabbMinX, aabbMinY, aabbMinZ, _)
//   col 1 = (aabbMaxX, aabbMaxY, aabbMaxZ, _)
//   col 2 = (sizeX, sizeY, sizeZ, atlasRowOffset)
//   col 3..6 = worldToShip mat4, one column per texel (column-major)
uniform sampler2D ValkyrienAir_ShipMetaTex;
uniform mat4 InverseProjMat;
uniform mat4 InverseViewMat;
uniform vec3 CameraWorldPos;
uniform int ValkyrienAir_ShipCount;

in vec2 texCoord;
out vec4 fragColor;

const int VA_MASK_TEX_WIDTH_SHIFT = 12;
const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;
const int VA_OCC_WORDS_PER_VOXEL = 16;

vec3 reconstructWorldPos(float depth) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InverseProjMat * clipPos;
    viewPos /= viewPos.w;
    // modelViewMatrix at this stage is rotation + bob-view + damage-tilt, with no world translation
    // (world geometry is rendered camera-relative). Inverting gets us a camera-relative world pos
    // that correctly undoes the bob / tilt offset; add CameraWorldPos to land in actual world space.
    vec3 cameraRelative = (InverseViewMat * vec4(viewPos.xyz, 1.0)).xyz;
    return CameraWorldPos + cameraRelative;
}

uint va_fetchAtlasWord(int wordIndex, int atlasRowOffset) {
    int row = (wordIndex >> VA_MASK_TEX_WIDTH_SHIFT) + atlasRowOffset;
    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, row);
    vec4 raw = texelFetch(ValkyrienAir_MaskAtlas, coord, 0) * 255.0;
    uvec4 bytes = uvec4(round(raw));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

bool va_testAir(int voxelIdx, ivec3 isize, int atlasRowOffset) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int wordIndex = occBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchAtlasWord(wordIndex, atlasRowOffset);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_sceneInShipPocket(vec3 worldPos, int shipIdx) {
    vec4 aabbMin = texelFetch(ValkyrienAir_ShipMetaTex, ivec2(0, shipIdx), 0);
    vec4 aabbMax = texelFetch(ValkyrienAir_ShipMetaTex, ivec2(1, shipIdx), 0);
    vec4 gridSize = texelFetch(ValkyrienAir_ShipMetaTex, ivec2(2, shipIdx), 0);
    if (gridSize.x <= 0.0) return false;
    if (worldPos.x < aabbMin.x || worldPos.x > aabbMax.x) return false;
    if (worldPos.y < aabbMin.y || worldPos.y > aabbMax.y) return false;
    if (worldPos.z < aabbMin.z || worldPos.z > aabbMax.z) return false;
    mat4 worldToShip = mat4(
        texelFetch(ValkyrienAir_ShipMetaTex, ivec2(3, shipIdx), 0),
        texelFetch(ValkyrienAir_ShipMetaTex, ivec2(4, shipIdx), 0),
        texelFetch(ValkyrienAir_ShipMetaTex, ivec2(5, shipIdx), 0),
        texelFetch(ValkyrienAir_ShipMetaTex, ivec2(6, shipIdx), 0)
    );
    vec3 shipPos = (worldToShip * vec4(worldPos, 1.0)).xyz;
    if (shipPos.x < 0.0 || shipPos.y < 0.0 || shipPos.z < 0.0) return false;
    if (shipPos.x >= gridSize.x || shipPos.y >= gridSize.y || shipPos.z >= gridSize.z) return false;
    ivec3 isize = ivec3(gridSize.xyz);
    ivec3 voxel = ivec3(floor(shipPos));
    int voxelIdx = voxel.x + isize.x * (voxel.y + isize.y * voxel.z);
    int atlasRowOffset = int(gridSize.w);
    return va_testAir(voxelIdx, isize, atlasRowOffset);
}

void main() {
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    if (sceneDepth >= 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Bias the sample toward the camera by a small fraction of a voxel so surfaces sitting right on
    // an air-voxel face (hull walls viewed from inside a pocket) reliably resolve into the air voxel
    // instead of jittering between the air voxel and its non-air neighbor with depth precision. That
    // jitter is what reads as z-fighting in the fog.
    vec3 sceneWorldPos = reconstructWorldPos(sceneDepth);
    vec3 viewDir = normalize(sceneWorldPos - CameraWorldPos);
    vec3 biasedPos = sceneWorldPos - viewDir * 0.05;

    // Direct mask lookup: R = 1 iff the scene fragment's voxel is marked interior in some ship's
    // mask. The fog shader reads this as a binary gate to skip pixels that are inside an air pocket.
    float sceneInPocket = 0.0;
    for (int i = 0; i < ValkyrienAir_ShipCount; i++) {
        if (va_sceneInShipPocket(biasedPos, i)) {
            sceneInPocket = 1.0;
            break;
        }
    }

    fragColor = vec4(sceneInPocket, 0.0, 0.0, 1.0);
}
