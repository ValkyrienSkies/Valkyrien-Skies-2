
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

#define VS_FLUID_ATLAS_ONLY
#moj_import <vs_fluid_occlusion.glsl>

#ifdef VS_RETAINED_FLUID
flat in int vs_FluidInterface;
#endif

#ifdef VS_TERRAIN_MASK
uniform int vs_MaskCutout;
uniform int vs_MaskUsesAlpha;
#endif

void main() {
#ifdef VS_RETAINED_FLUID
    if (vs_FluidInterface != 0 && !gl_FrontFacing) discard;
#endif
#ifndef VS_TERRAIN_MASK
    if (VsFluidOcclusionEnabled > 0.5 && vs_isFluidSprite(texCoord0)
        && vs_shouldCullOverflow(VsFluidOverflowDescriptors)) discard;

#endif

#ifdef VS_TERRAIN_MASK
    vec4 tex = vs_MaskCutout == 1 ? textureLod(Sampler0, texCoord0, 0.0) : texture(Sampler0, texCoord0);
    vec4 color = tex * vertexColor * ColorModulator;
    if ((vs_MaskCutout == 1 && color.a < 0.1) || (vs_MaskCutout == 2 && color.a < 0.5)) discard;
#elif defined(VS_MATERIAL_TERRAIN)
    vec4 color = vs_shipAtlasColor(Sampler0, texCoord0) * vertexColor * ColorModulator;
#else
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
#endif
    if (u_VsShipGlowEnabled != 0 || u_VsShipShadowStrength > 0.0) {
        vec3 vsWorldPos = valkyrienair_CamRelPos + u_VsShipLightCameraPos;
        float vsShipGlow = u_VsShipGlowEnabled != 0 ? vs_shipGlowSmooth(vsWorldPos, v_VsWorldNormal) : 0.0;
        vec2 vsAdjusted = v_VsLightCoordRaw;
        vsAdjusted.x = max(vsAdjusted.x, (vsShipGlow + 0.5) / 16.0);
        vec3 vsBase = texture(Sampler2, v_VsLightCoordRaw).rgb;
        vec3 vsLight = vs_shipShadowLight(Sampler2, vsAdjusted, vsWorldPos + v_VsWorldNormal * 0.04, v_VsWorldNormal).rgb;
        color.rgb *= vsLight / max(vsBase, vec3(1.0 / 255.0));
    }
#ifdef VS_MATERIAL_TERRAIN
    vs_applyShipMaterial(color);
#endif
#ifdef VS_TERRAIN_MASK
    fragColor = vec4(vs_MaskUsesAlpha != 0 ? color.a : 1.0);
#else
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
#endif
}
