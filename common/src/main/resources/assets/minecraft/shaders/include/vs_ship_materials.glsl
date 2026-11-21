#ifdef VS_SHIP_MATERIALS
in vec3 vs_ShipPos;
uniform ivec2 vs_MaterialRange;
uniform samplerBuffer vs_MaterialData;
uniform int vs_DeferredCutout;
const int VS_CUTOUT = 1;
const int VS_CUTOUT_MIPPED = 2;
#endif

vec4 vs_shipAtlasColor(sampler2D atlas, vec2 uv) {
#ifdef VS_SHIP_MATERIALS
    if (vs_DeferredCutout == VS_CUTOUT) return textureLod(atlas, uv, 0.0);
#endif
    return texture(atlas, uv);
}

void vs_applyShipMaterial(inout vec4 color) {
#ifdef VS_SHIP_MATERIALS
    if (vs_MaterialRange.y == 0) return;
    if (vs_DeferredCutout == VS_CUTOUT && color.a < 0.1) discard;
    if (vs_DeferredCutout == VS_CUTOUT_MIPPED && color.a < 0.5) discard;
    for (int i = 0; i < vs_MaterialRange.y; ++i) {
        int offset = vs_MaterialRange.x + i * 2;
        int id = int(texelFetch(vs_MaterialData, offset).x);
        vec4 params = texelFetch(vs_MaterialData, offset + 1);
        vs_applyRegisteredMaterial(id, vs_ShipPos, params, color);
    }
#endif
}
