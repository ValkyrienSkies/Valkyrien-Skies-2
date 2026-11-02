#version 150

#moj_import <fog.glsl>
#moj_import <vs_ship_glow_grid.glsl>
#moj_import <vs_ship_shadows.glsl>

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

#moj_import <vs_fluid_occlusion.glsl>

void main() {
    if (VsFluidOcclusionEnabled > 0.5 && vs_isFluidSprite(texCoord0)) {
        if (
            vs_shouldCullFluid(VsFluidOcclusionMask0, VsFluidOcclusionGridSize0, VsFluidOcclusionCameraLocal0, VsFluidOcclusionWorldToShip0, VsFluidOcclusionPartialCount0) ||
            vs_shouldCullFluid(VsFluidOcclusionMask1, VsFluidOcclusionGridSize1, VsFluidOcclusionCameraLocal1, VsFluidOcclusionWorldToShip1, VsFluidOcclusionPartialCount1) ||
            vs_shouldCullFluid(VsFluidOcclusionMask2, VsFluidOcclusionGridSize2, VsFluidOcclusionCameraLocal2, VsFluidOcclusionWorldToShip2, VsFluidOcclusionPartialCount2) ||
            vs_shouldCullFluid(VsFluidOcclusionMask3, VsFluidOcclusionGridSize3, VsFluidOcclusionCameraLocal3, VsFluidOcclusionWorldToShip3, VsFluidOcclusionPartialCount3) ||
            vs_shouldCullFluid(VsFluidOcclusionMask4, VsFluidOcclusionGridSize4, VsFluidOcclusionCameraLocal4, VsFluidOcclusionWorldToShip4, VsFluidOcclusionPartialCount4) ||
            vs_shouldCullFluid(VsFluidOcclusionMask5, VsFluidOcclusionGridSize5, VsFluidOcclusionCameraLocal5, VsFluidOcclusionWorldToShip5, VsFluidOcclusionPartialCount5) ||
            vs_shouldCullFluid(VsFluidOcclusionMask6, VsFluidOcclusionGridSize6, VsFluidOcclusionCameraLocal6, VsFluidOcclusionWorldToShip6, VsFluidOcclusionPartialCount6) ||
            vs_shouldCullFluid(VsFluidOcclusionMask7, VsFluidOcclusionGridSize7, VsFluidOcclusionCameraLocal7, VsFluidOcclusionWorldToShip7, VsFluidOcclusionPartialCount7) ||
            vs_shouldCullFluid(VsFluidOcclusionMask8, VsFluidOcclusionGridSize8, VsFluidOcclusionCameraLocal8, VsFluidOcclusionWorldToShip8, VsFluidOcclusionPartialCount8)
        ) {
            discard;
        }
        if (VsFluidOverflowCount > 0 && vs_shouldCullOverflow(VsFluidOcclusionMask0)) discard;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (u_VsShipGlowEnabled != 0 || u_VsShipShadowStrength > 0.0) {
        vec3 vsWorldPos = valkyrienair_CamRelPos + u_VsShipLightCameraPos;
        float vsShipGlow = u_VsShipGlowEnabled != 0 ? vs_shipGlowSmooth(vsWorldPos, v_VsWorldNormal) : 0.0;
        vec2 vsAdjusted = v_VsLightCoordRaw;
        vsAdjusted.x = max(vsAdjusted.x, (vsShipGlow + 0.5) / 16.0);
        vec3 vsBase = texture(Sampler2, v_VsLightCoordRaw).rgb;
        vec3 vsLight = vs_shipShadowLight(Sampler2, vsAdjusted, vsWorldPos + v_VsWorldNormal * 0.04, v_VsWorldNormal).rgb;
        color.rgb *= vsLight / max(vsBase, vec3(1.0 / 255.0));
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
