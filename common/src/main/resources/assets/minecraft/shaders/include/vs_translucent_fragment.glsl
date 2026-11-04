
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

void main() {
#ifdef VS_RETAINED_FLUID
    if (vs_FluidInterface != 0 && !gl_FrontFacing) discard;
#endif
    if (VsFluidOcclusionEnabled > 0.5 && vs_isFluidSprite(texCoord0)
        && vs_shouldCullOverflow(VsFluidOverflowDescriptors)) discard;

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
