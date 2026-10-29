uniform sampler2DArray u_VsShipShadowMap;
uniform sampler2DArrayShadow u_VsShipShadowCompare;
uniform int u_VsShipShadowMapEnabled;
uniform vec3 u_VsShipShadowOrigin;
uniform vec2 u_VsShipShadowHeight;
uniform vec4 u_VsShipShadowCascades[3];
uniform float u_VsShipShadowStrength;
uniform float u_VsShipShadowSoftness;

// Compare four depths, then interpolate the results. Do not interpolate the depths.
float vs_shadowCompare(vec2 uv, int layer, vec2 receiverXZ, float depth, vec2 slope, float bias) {
    vec2 size = vec2(textureSize(u_VsShipShadowMap, 0).xy);
    vec2 pixel = uv * size - 0.5;
    ivec2 first = ivec2(floor(pixel));
    vec2 weight = fract(pixel);
    vec4 map = u_VsShipShadowCascades[layer];
    vec4 visible;
    for (int i = 0; i < 4; i++) {
        ivec2 corner = ivec2(i & 1, (i >> 1) & 1);
        ivec2 texel = clamp(first + corner, ivec2(0), ivec2(size) - 1);
        float stored = texelFetch(u_VsShipShadowMap, ivec3(texel, layer), 0).r;
        vec2 sampleXZ = ((vec2(texel) + 0.5) / size * 2.0 - 1.0) * map.z + map.xy;
        float reference = depth + dot(slope, sampleXZ - receiverXZ) - bias;
        visible[i] = stored >= 1.0 || reference <= stored ? 1.0 : 0.0;
    }
    return mix(mix(visible.x, visible.y, weight.x), mix(visible.z, visible.w, weight.x), weight.y);
}

float vs_shadowCascade(vec3 pos, vec3 normal, int layer) {
    vec4 map = u_VsShipShadowCascades[layer];
    vec2 uv = (pos.xz - map.xy) / (2.0 * map.z) + 0.5;
    float depth = (u_VsShipShadowHeight.x - pos.y) / u_VsShipShadowHeight.y;
    if (depth <= 0.0) return 1.0;
    // Compare on the receiving plane to keep a tilted deck from shadowing itself.
    float normalY = normal.y < 0.0 ? min(normal.y, -0.0001) : max(normal.y, 0.0001);
    vec2 slope = clamp(normal.xz / normalY, -64.0, 64.0);
    slope /= u_VsShipShadowHeight.y;
    float bias = 0.025 / u_VsShipShadowHeight.y;
    float softness = max(u_VsShipShadowSoftness, 0.0);
    // A horizontal plane uses one filtered depth comparison for all four texels.
    bool horizontal = all(equal(slope, vec2(0.0))) && depth - bias <= 1.0;
    if (softness <= 0.0) {
        if (horizontal) return texture(u_VsShipShadowCompare, vec4(uv, float(layer), depth - bias));
        return vs_shadowCompare(uv, layer, pos.xz, depth, slope, bias);
    }
    float radius = min(8.0, softness);
    // Fixed disk samples avoid aligned filter rows at a moving silhouette.
    const vec2 offsets[16] = vec2[16](
        vec2(0.17677670, 0.00000000),
        vec2(-0.22577219, 0.20682582),
        vec2(0.03455805, -0.39377118),
        vec2(0.28457122, 0.37117276),
        vec2(-0.52222319, -0.09237393),
        vec2(0.49469539, -0.31468471),
        vec2(-0.16546593, 0.61552500),
        vec2(-0.31556147, -0.60759440),
        vec2(0.68464216, 0.25003022),
        vec2(-0.71225609, 0.29400896),
        vec2(0.34335450, -0.73372862),
        vec2(0.25373024, 0.80893199),
        vec2(-0.76474589, -0.44318588),
        vec2(0.89713398, -0.19723239),
        vec2(-0.54750690, 0.77877223),
        vec2(-0.12648677, -0.97608970));
    float sum = 0.0;
    if (horizontal) {
        for (int i = 0; i < 16; i++) {
            vec2 sampleUV = uv + offsets[i] * radius / (2.0 * map.z);
            sum += texture(u_VsShipShadowCompare, vec4(sampleUV, float(layer), depth - bias));
        }
    } else {
        for (int i = 0; i < 16; i++) {
            sum += vs_shadowCompare(uv + offsets[i] * radius / (2.0 * map.z), layer, pos.xz, depth, slope, bias);
        }
    }
    return sum / 16.0;
}

float vs_shipSkyVisibility(vec3 worldPos, vec3 worldNormal) {
    vec3 pos = worldPos - u_VsShipShadowOrigin;
    for (int layer = 0; layer < 3; layer++) {
        vec4 map = u_VsShipShadowCascades[layer];
        vec2 offset = abs(pos.xz - map.xy);
        float edge = map.z - max(offset.x, offset.y);
        // Leave room for the filter before changing maps.
        if (edge <= 9.0) continue;
        float visibility = vs_shadowCascade(pos, worldNormal, layer);
        if (edge >= 17.0) return visibility;
        float next = layer < 2 ? vs_shadowCascade(pos, worldNormal, layer + 1) : 1.0;
        return mix(next, visibility, smoothstep(9.0, 17.0, edge));
    }
    return 1.0;
}

vec4 vs_shipShadowLight(sampler2D lightmap, vec2 lightCoord, vec3 worldPos, vec3 worldNormal) {
    vec4 skyLight = texture(lightmap, lightCoord);
    if (u_VsShipShadowStrength <= 0.0 || u_VsShipShadowMapEnabled == 0) return skyLight;
    float shadow = (1.0 - vs_shipSkyVisibility(worldPos, worldNormal)) * clamp(u_VsShipShadowStrength, 0.0, 1.0);
    if (shadow <= 0.0) return skyLight;
    vec4 blockLight = texture(lightmap, vec2(lightCoord.x, 1.0 / 32.0));
    return mix(skyLight, blockLight, shadow);
}
