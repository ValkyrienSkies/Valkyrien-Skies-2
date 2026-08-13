#version 150

#moj_import <fog.glsl>
#moj_import <vs_ship_glow_grid.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;

in vec3 valkyrienair_CamRelPos;
in vec2 v_VsLightCoordRaw;
in vec3 v_VsWorldNormal;

uniform int u_VsShipGlowEnabled;
uniform vec3 u_VsShipLightCameraPos;

out vec4 fragColor;

uniform float VsFluidOcclusionEnabled;
uniform vec4 VsFluidOcclusionUv0;
uniform vec4 VsFluidOcclusionUv1;
uniform vec4 VsFluidOcclusionUv2;
uniform vec4 VsFluidOcclusionUv3;
uniform vec4 VsFluidOcclusionUv4;

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

const int VS_FLUID_MASK_WIDTH_SHIFT = 12;
const int VS_FLUID_MASK_WIDTH_MASK = 4095;
const float VS_FLUID_SAMPLE_EPSILON = 0.0001;

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

bool vs_shouldCullFluid(
    sampler2D mask,
    vec3 gridSize,
    vec3 cameraLocal,
    mat4 worldToShip
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
    int wordIndex = voxelIndex >> 5;
    int bitIndex = voxelIndex & 31;
    uint word = vs_fetchFluidMaskWord(mask, wordIndex);
    return ((word >> uint(bitIndex)) & 1u) != 0u;
}

void main() {
    if (VsFluidOcclusionEnabled > 0.5 && vs_isFluidSprite(texCoord0)) {
        if (
            vs_shouldCullFluid(VsFluidOcclusionMask0, VsFluidOcclusionGridSize0, VsFluidOcclusionCameraLocal0, VsFluidOcclusionWorldToShip0) ||
            vs_shouldCullFluid(VsFluidOcclusionMask1, VsFluidOcclusionGridSize1, VsFluidOcclusionCameraLocal1, VsFluidOcclusionWorldToShip1) ||
            vs_shouldCullFluid(VsFluidOcclusionMask2, VsFluidOcclusionGridSize2, VsFluidOcclusionCameraLocal2, VsFluidOcclusionWorldToShip2) ||
            vs_shouldCullFluid(VsFluidOcclusionMask3, VsFluidOcclusionGridSize3, VsFluidOcclusionCameraLocal3, VsFluidOcclusionWorldToShip3) ||
            vs_shouldCullFluid(VsFluidOcclusionMask4, VsFluidOcclusionGridSize4, VsFluidOcclusionCameraLocal4, VsFluidOcclusionWorldToShip4) ||
            vs_shouldCullFluid(VsFluidOcclusionMask5, VsFluidOcclusionGridSize5, VsFluidOcclusionCameraLocal5, VsFluidOcclusionWorldToShip5) ||
            vs_shouldCullFluid(VsFluidOcclusionMask6, VsFluidOcclusionGridSize6, VsFluidOcclusionCameraLocal6, VsFluidOcclusionWorldToShip6) ||
            vs_shouldCullFluid(VsFluidOcclusionMask7, VsFluidOcclusionGridSize7, VsFluidOcclusionCameraLocal7, VsFluidOcclusionWorldToShip7) ||
            vs_shouldCullFluid(VsFluidOcclusionMask8, VsFluidOcclusionGridSize8, VsFluidOcclusionCameraLocal8, VsFluidOcclusionWorldToShip8)
        ) {
            discard;
        }
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (u_VsShipGlowEnabled != 0) {
        vec3 vsWorldPos = valkyrienair_CamRelPos + u_VsShipLightCameraPos;
        float vsShipGlow = vs_shipGlowSmooth(vsWorldPos, v_VsWorldNormal);
        if (vsShipGlow > 0.0) {
            float vsBoostedU = max(v_VsLightCoordRaw.x, (vsShipGlow + 0.5) / 16.0);
            vec3 vsBase = texture(Sampler2, v_VsLightCoordRaw).rgb;
            vec3 vsBoosted = texture(Sampler2, vec2(vsBoostedU, v_VsLightCoordRaw.y)).rgb;
            color.rgb *= vsBoosted / max(vsBase, vec3(1.0 / 255.0));
        }
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
