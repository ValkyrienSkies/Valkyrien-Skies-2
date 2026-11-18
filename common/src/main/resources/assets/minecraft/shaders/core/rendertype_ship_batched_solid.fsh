#version 330 core

#moj_import <vs_batched_ship_color.glsl>

noperspective in vec2 v_VsMotion;
layout(location = 1) out vec4 vsMotion;

void main() {
    vec3 normal = vs_shipWorldNormal();
    vec4 color = vs_shipAtlasColor(Sampler0, texCoord0) * vertexColor * ColorModulator;
    vs_lightShipColor(color, normal);
    vs_applyShipMaterial(color);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    // b marks the pixel as ship and a is the depth it was drawn at, which the consumer checks
    // against the scene depth to tell whether something drawn later has covered it.
    vsMotion = vec4(v_VsMotion, 1.0, gl_FragCoord.z);
}
