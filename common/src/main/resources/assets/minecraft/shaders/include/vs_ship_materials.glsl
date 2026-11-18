#ifdef VS_SHIP_MATERIALS
in vec3 vs_ShipPos;
uniform int vs_MaterialId;
uniform vec4 vs_MaterialParams;
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
    if (vs_MaterialId == 0) return;
    if (vs_DeferredCutout == VS_CUTOUT && color.a < 0.1) discard;
    if (vs_DeferredCutout == VS_CUTOUT_MIPPED && color.a < 0.5) discard;
    vs_applyRegisteredMaterial(vs_MaterialId, vs_ShipPos, vs_MaterialParams, color);
#endif
}
