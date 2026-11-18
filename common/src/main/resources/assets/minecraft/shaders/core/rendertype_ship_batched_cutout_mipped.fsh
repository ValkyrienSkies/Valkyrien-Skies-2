#version 330 core

#moj_import <vs_batched_ship_color.glsl>

void main() {
    vec3 normal = vs_shipWorldNormal();
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.5) discard;
    vs_lightShipColor(color, normal);
    vs_applyShipMaterial(color);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
