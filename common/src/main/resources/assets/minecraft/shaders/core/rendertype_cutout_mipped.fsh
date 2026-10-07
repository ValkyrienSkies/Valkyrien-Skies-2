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

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.5) {
        discard;
    }
    if (u_VsShipGlowEnabled != 0) {
        vec3 vsWorldPos = valkyrienair_CamRelPos + u_VsShipLightCameraPos;
        float vsShipGlow = vs_shipGlowSmooth(vsWorldPos, v_VsWorldNormal);
        vec2 vsAdjusted = v_VsLightCoordRaw;
        vsAdjusted.x = max(vsAdjusted.x, (vsShipGlow + 0.5) / 16.0);
        vec3 vsBase = texture(Sampler2, v_VsLightCoordRaw).rgb;
        vec3 vsLight = vs_shipShadowLight(Sampler2, vsAdjusted, vsWorldPos + v_VsWorldNormal * 0.04, v_VsWorldNormal).rgb;
        color.rgb *= vsLight / max(vsBase, vec3(1.0 / 255.0));
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
