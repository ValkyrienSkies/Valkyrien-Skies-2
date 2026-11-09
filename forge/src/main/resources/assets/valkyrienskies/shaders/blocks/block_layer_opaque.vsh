#version 330 core

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>
#import <sodium:include/chunk_material.glsl>

// u_TransformMatrix: ship-to-world matrix, used to lift the per-quad face
// normal into world space. Needed when shade is on, when world-light runs, or
// when ship-on-ship AO projects occluders onto this face.
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_SHADE) || defined(VS_SHIP_ON_SHIP)
uniform mat4 u_TransformMatrix;
#endif
// u_LocalToCameraRel: maps sodium's chunk-local pos into camera-relative
// world space. Needed by FSH light pipeline (via v_CameraRelWorldPos) or by
// the biome lookup (worldPosVertex), or ship-on-ship AO.
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_BIOME) || defined(VS_SHIP_ON_SHIP)
uniform mat4 u_LocalToCameraRel;
uniform ivec3 u_VsRenderOrigin;
#endif
#ifdef VS_DYNAMIC_BIOME
uniform usamplerBuffer u_VsBiomeSections;
uniform usamplerBuffer u_VsBiomeLut;
#endif

out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_BakedLightCoord;
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_SHIP_ON_SHIP)
out vec3 v_CameraRelWorldPos;
#endif
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_SHADE) || defined(VS_SHIP_ON_SHIP)
flat out vec3 v_WorldNormal;
#endif
// Decoded VS vertex flags. See VsVertexFlagPacker:
//   alpha bits 0-2: AO level (0..5 mapped to 0/0.2/0.4/0.6/0.8/1.0)
//   alpha bits 3-5: face slot (0 DOWN, 1 UP, 2 N, 3 S, 4 W, 5 E, 6 UNSHADED)
//   alpha bits 6-7: resolverType (0 none, 1 grass, 2 foliage, 3 water)
flat out int v_ResolverType;
flat out int v_IsShaded;
flat out int v_IsFullbright;
out vec3 v_VertexBiomeTint;

out float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
out float v_MaterialAlphaCutoff;
#endif

#ifdef USE_FOG
out float v_FragDistance;
#endif

uniform int u_FogShape;
uniform vec3 u_RegionOffset;

uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

#ifdef VS_DYNAMIC_BIOME
// ===== Per-vertex biome color lookup =====================================
const uint VS_BIOME_CELLS_PER_RESOLVER = 256u;
const uint VS_BIOME_SECTION_SIZE_INTS = 768u;

uint vs_indexBiomeLut(uint i) { return texelFetch(u_VsBiomeLut, int(i)).r; }
uint vs_indexBiome(uint i) { return texelFetch(u_VsBiomeSections, int(i)).r; }

bool vs_nextBiomeLut(uint base, int coord, out uint next) {
    int start = int(vs_indexBiomeLut(base));
    uint size = vs_indexBiomeLut(base + 1u);
    int idx = coord - start;
    if (idx < 0 || idx >= int(size)) return true;
    next = vs_indexBiomeLut(base + 2u + uint(idx));
    return false;
}

bool vs_chunkCoordToBiomeSectionIndex(ivec3 sectionPos, out uint index) {
    uint first;
    if (vs_nextBiomeLut(0u, sectionPos.y, first) || first == 0u) return true;
    uint second;
    if (vs_nextBiomeLut(first, sectionPos.x, second) || second == 0u) return true;
    uint sectionIndex;
    if (vs_nextBiomeLut(second, sectionPos.z, sectionIndex) || sectionIndex == 0u) return true;
    index = sectionIndex - 1u;
    return false;
}

vec3 vs_unpackRgb8(uint v) {
    return vec3(
        float( v        & 0xFFu),
        float((v >>  8u) & 0xFFu),
        float((v >> 16u) & 0xFFu)
    ) * (1.0 / 255.0);
}

vec3 vs_biomeColorAt(vec3 worldPos, int resolverSlot) {
    ivec3 blockPos = ivec3(floor(worldPos));
    uint sectionIndex;
    if (vs_chunkCoordToBiomeSectionIndex(blockPos >> 4, sectionIndex)) {
        return vec3(1.0);
    }
    uint sectionOffset = sectionIndex * VS_BIOME_SECTION_SIZE_INTS;
    ivec2 cell = blockPos.xz & 15;
    uint cellOffset = uint(cell.x) + uint(cell.y) * 16u;
    uint addr = sectionOffset
              + uint(resolverSlot) * VS_BIOME_CELLS_PER_RESOLVER
              + cellOffset;
    return vs_unpackRgb8(vs_indexBiome(addr));
}
#endif

vec3 vs_faceSlotToNormal(uint slot) {
    if (slot == 0u) return vec3(0.0, -1.0, 0.0);
    if (slot == 1u) return vec3(0.0,  1.0, 0.0);
    if (slot == 2u) return vec3(0.0,  0.0, -1.0);
    if (slot == 3u) return vec3(0.0,  0.0,  1.0);
    if (slot == 4u) return vec3(-1.0, 0.0, 0.0);
    if (slot == 5u) return vec3( 1.0, 0.0, 0.0);
    return vec3(0.0, 1.0, 0.0);
}
// =========================================================================

void main() {
    _vert_init();

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

#if defined(VS_DYNAMIC_LIGHT) || defined(VS_SHIP_ON_SHIP)
    // Camera-relative world position. The fragment adds u_VsRenderOrigin to
    // recover the absolute world block position; per-fragment interpolation
    // means each fragment lands inside a block (not at a corner), avoiding the
    // float-precision flicker that per-vertex lookups had at section faces.
    v_CameraRelWorldPos = (u_LocalToCameraRel * vec4(position, 1.0)).xyz;
#endif

#ifdef USE_FOG
    v_FragDistance = getFragDistance(u_FogShape, position);
#endif

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Embeddium's _vert_tex_light_coord is ivec2 holding light_value*16 bytes
    // (range [0, 240]); the fragment shader expects the [0, 1] UV form.
    v_BakedLightCoord = vec2(_vert_tex_light_coord) / 256.0;
    // Decode the new alpha layout: ao(3) | face(3) | resolver(2). AO uses a
    // custom mapping where the integer level maps to ao = level * 0.2, which
    // exactly captures sodium's discrete AO values {0, 0.2, 0.4, 0.6, 0.8, 1.0}.
    uint aoByte = uint(_vert_color.a * 255.0 + 0.5);
    uint aoLevel = aoByte & 7u;
    uint faceSlot = (aoByte >> 3u) & 7u;
    v_ResolverType = int((aoByte >> 6u) & 3u);
    // faceSlot 0-5 = shaded cardinal, 6 = unshaded (fluids etc.), 7 = fullbright (emissive)
    v_IsShaded = (faceSlot < 6u) ? 1 : 0;
    v_IsFullbright = (faceSlot == 7u) ? 1 : 0;
    float aoFloat = float(aoLevel) * 0.2;
    v_Color = vec4(_vert_color.rgb, aoFloat);

#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_SHADE) || defined(VS_SHIP_ON_SHIP)
    // World-space surface normal: shipyard-space face direction transformed by
    // the ship-to-world matrix. All four vertices of a quad share the same face
    // slot, so flat-interpolating this through the rasterizer is exact.
    vec3 shipyardNormal = vs_faceSlotToNormal(faceSlot);
    v_WorldNormal = normalize((u_TransformMatrix * vec4(shipyardNormal, 0.0)).xyz);
#endif

#ifdef VS_DYNAMIC_BIOME
    // Per-vertex biome lookup at the absolute world position. Linear-blended
    // across the quad by the rasterizer for smooth biome transitions; vec3(1.0)
    // when the quad isn't biome-tinted so the FSH multiply is a no-op.
    vec3 worldPosVertex = (u_LocalToCameraRel * vec4(position, 1.0)).xyz + vec3(u_VsRenderOrigin);
    v_VertexBiomeTint = (v_ResolverType > 0)
            ? vs_biomeColorAt(worldPosVertex, v_ResolverType - 1)
            : vec3(1.0);
#else
    v_VertexBiomeTint = vec3(1.0);
#endif

    v_TexCoord = _vert_tex_diffuse_coord;

    v_MaterialMipBias = _material_mip_bias(_material_params);
#ifdef USE_FRAGMENT_DISCARD
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
#endif
}
