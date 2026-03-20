#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D ValkyrienAir_FluidMask;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;

in vec3 valkyrienair_CamRelPos;

out vec4 fragColor;

uniform float ValkyrienAir_CullEnabled;
uniform float ValkyrienAir_IsShipPass;
uniform vec3 ValkyrienAir_CameraWorldPos;

uniform vec4 ValkyrienAir_WaterStillUv;
uniform vec4 ValkyrienAir_WaterFlowUv;
uniform vec4 ValkyrienAir_WaterOverlayUv;
uniform float ValkyrienAir_ShipWaterTintEnabled;
uniform vec3 ValkyrienAir_ShipWaterTint;

uniform vec4 ValkyrienAir_ShipAabbMin0;
uniform vec4 ValkyrienAir_ShipAabbMax0;
uniform vec3 ValkyrienAir_CameraShipPos0;
uniform vec4 ValkyrienAir_GridMin0;
uniform vec4 ValkyrienAir_GridSize0;
uniform mat4 ValkyrienAir_WorldToShip0;
uniform usampler2D ValkyrienAir_Mask0;

uniform vec4 ValkyrienAir_ShipAabbMin1;
uniform vec4 ValkyrienAir_ShipAabbMax1;
uniform vec3 ValkyrienAir_CameraShipPos1;
uniform vec4 ValkyrienAir_GridMin1;
uniform vec4 ValkyrienAir_GridSize1;
uniform mat4 ValkyrienAir_WorldToShip1;
uniform usampler2D ValkyrienAir_Mask1;

uniform vec4 ValkyrienAir_ShipAabbMin2;
uniform vec4 ValkyrienAir_ShipAabbMax2;
uniform vec3 ValkyrienAir_CameraShipPos2;
uniform vec4 ValkyrienAir_GridMin2;
uniform vec4 ValkyrienAir_GridSize2;
uniform mat4 ValkyrienAir_WorldToShip2;
uniform usampler2D ValkyrienAir_Mask2;

uniform vec4 ValkyrienAir_ShipAabbMin3;
uniform vec4 ValkyrienAir_ShipAabbMax3;
uniform vec3 ValkyrienAir_CameraShipPos3;
uniform vec4 ValkyrienAir_GridMin3;
uniform vec4 ValkyrienAir_GridSize3;
uniform mat4 ValkyrienAir_WorldToShip3;
uniform usampler2D ValkyrienAir_Mask3;

uniform vec4 ValkyrienAir_ShipAabbMin4;
uniform vec4 ValkyrienAir_ShipAabbMax4;
uniform vec3 ValkyrienAir_CameraShipPos4;
uniform vec4 ValkyrienAir_GridMin4;
uniform vec4 ValkyrienAir_GridSize4;
uniform mat4 ValkyrienAir_WorldToShip4;
uniform usampler2D ValkyrienAir_Mask4;

uniform vec4 ValkyrienAir_ShipAabbMin5;
uniform vec4 ValkyrienAir_ShipAabbMax5;
uniform vec3 ValkyrienAir_CameraShipPos5;
uniform vec4 ValkyrienAir_GridMin5;
uniform vec4 ValkyrienAir_GridSize5;
uniform mat4 ValkyrienAir_WorldToShip5;
uniform usampler2D ValkyrienAir_Mask5;

uniform vec4 ValkyrienAir_ShipAabbMin6;
uniform vec4 ValkyrienAir_ShipAabbMax6;
uniform vec3 ValkyrienAir_CameraShipPos6;
uniform vec4 ValkyrienAir_GridMin6;
uniform vec4 ValkyrienAir_GridSize6;
uniform mat4 ValkyrienAir_WorldToShip6;
uniform usampler2D ValkyrienAir_Mask6;

uniform vec4 ValkyrienAir_ShipAabbMin7;
uniform vec4 ValkyrienAir_ShipAabbMax7;
uniform vec3 ValkyrienAir_CameraShipPos7;
uniform vec4 ValkyrienAir_GridMin7;
uniform vec4 ValkyrienAir_GridSize7;
uniform mat4 ValkyrienAir_WorldToShip7;
uniform usampler2D ValkyrienAir_Mask7;

uniform vec4 ValkyrienAir_ShipAabbMin8;
uniform vec4 ValkyrienAir_ShipAabbMax8;
uniform vec3 ValkyrienAir_CameraShipPos8;
uniform vec4 ValkyrienAir_GridMin8;
uniform vec4 ValkyrienAir_GridSize8;
uniform mat4 ValkyrienAir_WorldToShip8;
uniform usampler2D ValkyrienAir_Mask8;

const int VA_MASK_TEX_WIDTH_SHIFT = 12;
const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;

const int VA_SUB = 8;
const int VA_OCC_WORDS_PER_VOXEL = 16;

bool va_inUv(vec2 uv, vec4 bounds) {
    return uv.x >= bounds.x && uv.x <= bounds.z && uv.y >= bounds.y && uv.y <= bounds.w;
}

bool va_isWaterUv(vec2 uv) {
    return va_inUv(uv, ValkyrienAir_WaterStillUv) ||
        va_inUv(uv, ValkyrienAir_WaterFlowUv) ||
        va_inUv(uv, ValkyrienAir_WaterOverlayUv);
}

bool va_isFluidUv(vec2 uv) {
    return texture(ValkyrienAir_FluidMask, uv).r > 0.5;
}

uint va_fetchWord(usampler2D tex, int wordIndex) {
    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);
    return texelFetch(tex, coord, 0).r;
}

bool va_testAir(usampler2D mask, int voxelIdx, ivec3 isize) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int wordIndex = occBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_testOcc(usampler2D mask, int voxelIdx, int subIdx) {
    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);
    int bit = subIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_shouldDiscardForShip0(vec3 worldPos) {
    if (ValkyrienAir_GridSize0.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin0.x || worldPos.x > ValkyrienAir_ShipAabbMax0.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin0.y || worldPos.y > ValkyrienAir_ShipAabbMax0.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin0.z || worldPos.z > ValkyrienAir_ShipAabbMax0.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip0 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos0;
    vec3 localPos = shipPos - ValkyrienAir_GridMin0.xyz;
    vec3 size = ValkyrienAir_GridSize0.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask0, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask0, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip1(vec3 worldPos) {
    if (ValkyrienAir_GridSize1.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin1.x || worldPos.x > ValkyrienAir_ShipAabbMax1.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin1.y || worldPos.y > ValkyrienAir_ShipAabbMax1.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin1.z || worldPos.z > ValkyrienAir_ShipAabbMax1.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip1 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos1;
    vec3 localPos = shipPos - ValkyrienAir_GridMin1.xyz;
    vec3 size = ValkyrienAir_GridSize1.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask1, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask1, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip2(vec3 worldPos) {
    if (ValkyrienAir_GridSize2.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin2.x || worldPos.x > ValkyrienAir_ShipAabbMax2.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin2.y || worldPos.y > ValkyrienAir_ShipAabbMax2.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin2.z || worldPos.z > ValkyrienAir_ShipAabbMax2.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip2 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos2;
    vec3 localPos = shipPos - ValkyrienAir_GridMin2.xyz;
    vec3 size = ValkyrienAir_GridSize2.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask2, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask2, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip3(vec3 worldPos) {
    if (ValkyrienAir_GridSize3.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin3.x || worldPos.x > ValkyrienAir_ShipAabbMax3.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin3.y || worldPos.y > ValkyrienAir_ShipAabbMax3.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin3.z || worldPos.z > ValkyrienAir_ShipAabbMax3.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip3 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos3;
    vec3 localPos = shipPos - ValkyrienAir_GridMin3.xyz;
    vec3 size = ValkyrienAir_GridSize3.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask3, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask3, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip4(vec3 worldPos) {
    if (ValkyrienAir_GridSize4.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin4.x || worldPos.x > ValkyrienAir_ShipAabbMax4.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin4.y || worldPos.y > ValkyrienAir_ShipAabbMax4.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin4.z || worldPos.z > ValkyrienAir_ShipAabbMax4.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip4 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos4;
    vec3 localPos = shipPos - ValkyrienAir_GridMin4.xyz;
    vec3 size = ValkyrienAir_GridSize4.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask4, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask4, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip5(vec3 worldPos) {
    if (ValkyrienAir_GridSize5.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin5.x || worldPos.x > ValkyrienAir_ShipAabbMax5.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin5.y || worldPos.y > ValkyrienAir_ShipAabbMax5.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin5.z || worldPos.z > ValkyrienAir_ShipAabbMax5.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip5 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos5;
    vec3 localPos = shipPos - ValkyrienAir_GridMin5.xyz;
    vec3 size = ValkyrienAir_GridSize5.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask5, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask5, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip6(vec3 worldPos) {
    if (ValkyrienAir_GridSize6.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin6.x || worldPos.x > ValkyrienAir_ShipAabbMax6.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin6.y || worldPos.y > ValkyrienAir_ShipAabbMax6.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin6.z || worldPos.z > ValkyrienAir_ShipAabbMax6.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip6 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos6;
    vec3 localPos = shipPos - ValkyrienAir_GridMin6.xyz;
    vec3 size = ValkyrienAir_GridSize6.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask6, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask6, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip7(vec3 worldPos) {
    if (ValkyrienAir_GridSize7.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin7.x || worldPos.x > ValkyrienAir_ShipAabbMax7.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin7.y || worldPos.y > ValkyrienAir_ShipAabbMax7.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin7.z || worldPos.z > ValkyrienAir_ShipAabbMax7.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip7 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos7;
    vec3 localPos = shipPos - ValkyrienAir_GridMin7.xyz;
    vec3 size = ValkyrienAir_GridSize7.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask7, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask7, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardForShip8(vec3 worldPos) {
    if (ValkyrienAir_GridSize8.x <= 0.0) return false;
    if (worldPos.x < ValkyrienAir_ShipAabbMin8.x || worldPos.x > ValkyrienAir_ShipAabbMax8.x) return false;
    if (worldPos.y < ValkyrienAir_ShipAabbMin8.y || worldPos.y > ValkyrienAir_ShipAabbMax8.y) return false;
    if (worldPos.z < ValkyrienAir_ShipAabbMin8.z || worldPos.z > ValkyrienAir_ShipAabbMax8.z) return false;

    vec3 shipPos = (ValkyrienAir_WorldToShip8 * vec4(valkyrienair_CamRelPos, 0.0)).xyz + ValkyrienAir_CameraShipPos8;
    vec3 localPos = shipPos - ValkyrienAir_GridMin8.xyz;
    vec3 size = ValkyrienAir_GridSize8.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(shipPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(ValkyrienAir_Mask8, voxelIdx, subIdx)) return true;
    if (va_testAir(ValkyrienAir_Mask8, voxelIdx, isize)) return true;
    return false;
}

void main() {
    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5 && va_isFluidUv(texCoord0)) {
        vec3 worldPos = valkyrienair_CamRelPos + ValkyrienAir_CameraWorldPos;
        if (va_shouldDiscardForShip0(worldPos) || va_shouldDiscardForShip1(worldPos) ||
            va_shouldDiscardForShip2(worldPos) || va_shouldDiscardForShip3(worldPos) ||
            va_shouldDiscardForShip4(worldPos) || va_shouldDiscardForShip5(worldPos) ||
            va_shouldDiscardForShip6(worldPos) || va_shouldDiscardForShip7(worldPos) ||
            va_shouldDiscardForShip8(worldPos)) {
            discard;
        }
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (ValkyrienAir_ShipWaterTintEnabled > 0.5 && va_isWaterUv(texCoord0)) {
        color.rgb *= ValkyrienAir_ShipWaterTint;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
