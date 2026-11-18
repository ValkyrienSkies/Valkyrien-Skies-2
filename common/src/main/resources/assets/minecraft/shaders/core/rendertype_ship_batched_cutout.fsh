#version 330 core

#moj_import <vs_batched_ship_color.glsl>

noperspective in vec2 v_VsMotion;
layout(location = 1) out vec4 vsMotion;

void main() {
    vec3 normal = vs_shipWorldNormal();
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) discard;
    vs_lightShipColor(color, normal);
    vs_applyShipMaterial(color);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    vsMotion = vec4(v_VsMotion, 0.0, 1.0);
}
