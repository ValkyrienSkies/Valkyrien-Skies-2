#version 330 core

#moj_import <fog.glsl>
#moj_import <vs_batched_ship_glow.glsl>

uniform sampler2D Sampler0;   // block atlas
uniform sampler2D Sampler2;   // lightmap

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform ivec3 u_VsRenderOrigin;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 v_WorldLightCoord;
in vec3 v_CameraRelWorldPos;
flat in vec3 v_WorldNormal;
flat in float v_Fullbright;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec3 relPos = v_CameraRelWorldPos - v_WorldNormal * 0.02;
    vec3 worldPos = vec3(ivec3(floor(relPos)) + u_VsRenderOrigin) + fract(relPos);

    vec2 lightCoord = v_WorldLightCoord;
    if (v_Fullbright < 0.5) {
        float shipGlow = vs_shipGlowSmooth(worldPos, v_WorldNormal);
        if (shipGlow > 0.0) lightCoord.x = max(lightCoord.x, (shipGlow + 0.5) / 16.0);
    }

    vec4 lightSample = texture(Sampler2, lightCoord);
    vec4 color = tex * vertexColor * ColorModulator;
    color.rgb *= lightSample.rgb;

    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
