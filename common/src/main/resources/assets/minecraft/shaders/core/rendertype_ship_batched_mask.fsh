#version 330 core

#moj_import <vs_batched_ship_color.glsl>

uniform int vs_MaskCutout;
uniform int vs_MaskUsesAlpha;

void main() {
    vec3 normal = vs_shipWorldNormal();
    vec4 tex = vs_MaskCutout == 1 ? textureLod(Sampler0, texCoord0, 0.0) : texture(Sampler0, texCoord0);
    vec4 color = tex * vertexColor * ColorModulator;
    if ((vs_MaskCutout == 1 && color.a < 0.1) || (vs_MaskCutout == 2 && color.a < 0.5)) discard;
#ifdef VS_SHIP_MATERIALS
    if (vs_MaterialRange.y != 0) {
        vs_lightShipColor(color, normal);
        vs_applyShipMaterial(color);
    }
#endif
    fragColor = vec4(vs_MaskUsesAlpha != 0 ? color.a : 1.0);
}
