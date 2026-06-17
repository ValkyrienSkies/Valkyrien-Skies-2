#version 330 core

#import <sodium:include/fog.glsl>

in vec4 v_Color;            // RGB = sodium-baked vertex color; .a = AO (decoded in VSH)
in vec2 v_TexCoord;
in float v_FragDistance;
in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;
in vec2 v_BakedLightCoord;   // _vert_tex_light_coord (baked from shipyard storage)
flat in int v_ResolverType;  // 0 none, 1 grass, 2 foliage, 3 water
flat in int v_IsShaded;      // 0 unshaded (skip directional shade), 1 shaded
flat in int v_IsFullbright;  // 1 if BakedQuad was tagged emissive in its source model JSON
in vec3 v_VertexBiomeTint;   // rasterizer-blended world biome RGB, vec3(1.0) on non-biome quads
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_SHIP_ON_SHIP)
in vec3 v_CameraRelWorldPos; // camera-relative WORLD pos; +u_VsRenderOrigin == absolute world pos
#endif
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_SHADE) || defined(VS_SHIP_ON_SHIP)
flat in vec3 v_WorldNormal;  // world-space surface normal recovered from face slot in the VSH
#endif

uniform sampler2D u_BlockTex;
uniform sampler2D u_LightTex;

uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;

#if defined(VS_DYNAMIC_LIGHT) || defined(VS_SHIP_ON_SHIP)
uniform ivec3 u_VsRenderOrigin;
#endif
#ifdef VS_DYNAMIC_LIGHT
uniform usamplerBuffer u_VsLightSections;
uniform usamplerBuffer u_VsLightLut;
#endif
#ifdef VS_SHIP_ON_SHIP
// Per-frame ship-emitter list. TWO RGBA32F texels per emitter:
//   texel 2i:   vec4(worldX, worldY, worldZ, lightLevel)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation quaternion
// Manhattan distance is taken in the emitter's owning-ship local frame
// (offset rotated by q^-1) so the octahedral light bubble rotates with
// the hull.
uniform samplerBuffer u_VsShipEmitters;
uniform int u_VsShipEmitterCount;
// Per-frame solid ship voxel list (same 2-texel layout, position .w = 0).
// Used for ship-to-ship AO so one ship's voxels can cast smooth-tracking
// octagonal shadows on another ship's surface (and on the same ship's
// own concave faces).
uniform samplerBuffer u_VsShipOccluders;
uniform int u_VsShipOccluderCount;

// Inverse-rotate v by quaternion q (apply q^-1 = (-q.xyz, q.w) to v) so
// the SDF / distance metrics line up with the owning ship's axes.
vec3 vs_sosQuatRotateInv(vec4 q, vec3 v) {
    vec3 qNeg = -q.xyz;
    return v + 2.0 * cross(qNeg, cross(qNeg, v) + q.w * v);
}
#endif

out vec4 fragColor;

#define MINECRAFT_LIGHT_X (0.6)
#define MINECRAFT_LIGHT_Z (0.8)
#define MINECRAFT_LIGHT_Y (0.5)

// from Flywheel/common/src/backend/resources/assets/flywheel/flywheel/internal/diffuse.glsl
float vanillaShadeFromNormal(vec3 normal) {
    vec3 n2 = normal * normal * vec3(.6, .25, .8);
    return min(n2.x + n2.y * (3. + normal.y) + n2.z, 1.);
}

// MC's lightmap texture uses GL's default GL_REPEAT wrap; at UV=0 a LINEAR
// sample blends pixel 15 (bright) into pixel 0 (dark), giving the wrong color
// for sky=0 in caves. Clamp to pixel-center range to match sodium's baked
// vertex format (which clamps the packed light byte to [8, 248] for the same
// reason). Outside the VS_DYNAMIC_LIGHT block because the off-path still
// needs to clamp v_BakedLightCoord with these bounds.
const float VS_UV_MIN = 1.0 / 32.0;
const float VS_UV_MAX = 31.0 / 32.0;

#ifdef VS_DYNAMIC_LIGHT
// ===== Flywheel-style smooth light + AO ======================================
// Layout matches Flywheel's light_lut.glsl: each section is
//   [solid bits (732 B = 183 ints)] [light bytes (5832 B = 1458 ints)]
// for a total of 6564 bytes / 1641 ints per section. Solid bit + light byte at
// the same in-section position N use the same offset formula below.
const uint VS_BLOCKS_PER_SECTION = 18u * 18u * 18u;
const uint VS_LIGHT_SIZE_BYTES = VS_BLOCKS_PER_SECTION;
const uint VS_SOLID_SIZE_BYTES = ((VS_BLOCKS_PER_SECTION + 31u) / 32u) * 4u;
const uint VS_SOLID_START_INTS = 0u;
const uint VS_LIGHT_START_INTS = VS_SOLID_SIZE_BYTES / 4u;
const uint VS_SECTION_SIZE_INTS = (VS_SOLID_SIZE_BYTES + VS_LIGHT_SIZE_BYTES) / 4u;

const uint VS_COMPLETELY_SOLID = 0x7FFFFFFu;
const float VS_EPSILON = 1e-5;
const uint VS_LOWER_10_BITS = 0x3FFu;
const uint VS_UPPER_10_BITS = 0xFFF00000u;
const float VS_LIGHT_NORMALIZER = 1.0 / 16.0;

uint vs_indexLut(uint i) { return texelFetch(u_VsLightLut, int(i)).r; }
uint vs_indexLight(uint i) { return texelFetch(u_VsLightSections, int(i)).r; }

bool vs_nextLut(uint base, int coord, out uint next) {
    int start = int(vs_indexLut(base));
    uint size = vs_indexLut(base + 1u);
    int idx = coord - start;
    if (idx < 0 || idx >= int(size)) return true;
    next = vs_indexLut(base + 2u + uint(idx));
    return false;
}

bool vs_chunkCoordToSectionIndex(ivec3 sectionPos, out uint index) {
    uint first;
    if (vs_nextLut(0u, sectionPos.y, first) || first == 0u) return true;
    uint second;
    if (vs_nextLut(first, sectionPos.x, second) || second == 0u) return true;
    uint sectionIndex;
    if (vs_nextLut(second, sectionPos.z, sectionIndex) || sectionIndex == 0u) return true;
    index = sectionIndex - 1u;
    return false;
}

uvec2 vs_lightAt(uint sectionOffset, uvec3 blockInSectionPos) {
    uint byteOffset = blockInSectionPos.x + blockInSectionPos.z * 18u + blockInSectionPos.y * 18u * 18u;
    uint uintOffset = byteOffset >> 2u;
    uint bitOffset = (byteOffset & 3u) << 3u;
    uint raw = vs_indexLight(sectionOffset + VS_LIGHT_START_INTS + uintOffset);
    uint b = (raw >> bitOffset) & 0xFu;
    uint s = (raw >> (bitOffset + 4u)) & 0xFu;
    return uvec2(b, s);
}

bool vs_isSolid(uint sectionOffset, uvec3 blockInSectionPos) {
    uint bitOffset = blockInSectionPos.x + blockInSectionPos.z * 18u + blockInSectionPos.y * 18u * 18u;
    uint uintOffset = bitOffset >> 5u;
    uint bitInWordOffset = bitOffset & 31u;
    uint word = vs_indexLight(sectionOffset + VS_SOLID_START_INTS + uintOffset);
    return (word & (1u << bitInWordOffset)) != 0u;
}

uint vs_fetchSolid3x3x3(uint sectionOffset, ivec3 blockInSectionPos) {
    uint ret = 0u;
    #define VS_FETCH_SOLID(x, y, z, i) { \
        bool flag = vs_isSolid(sectionOffset, uvec3(blockInSectionPos + ivec3(x, y, z))); \
        ret |= uint(flag) << uint(i); \
    }
    VS_FETCH_SOLID(-1, -1, -1, 0)  VS_FETCH_SOLID(0, -1, -1, 1)  VS_FETCH_SOLID(1, -1, -1, 2)
    VS_FETCH_SOLID(-1, -1,  0, 3)  VS_FETCH_SOLID(0, -1,  0, 4)  VS_FETCH_SOLID(1, -1,  0, 5)
    VS_FETCH_SOLID(-1, -1,  1, 6)  VS_FETCH_SOLID(0, -1,  1, 7)  VS_FETCH_SOLID(1, -1,  1, 8)
    VS_FETCH_SOLID(-1,  0, -1, 9)  VS_FETCH_SOLID(0,  0, -1,10)  VS_FETCH_SOLID(1,  0, -1,11)
    VS_FETCH_SOLID(-1,  0,  0,12)  VS_FETCH_SOLID(0,  0,  0,13)  VS_FETCH_SOLID(1,  0,  0,14)
    VS_FETCH_SOLID(-1,  0,  1,15)  VS_FETCH_SOLID(0,  0,  1,16)  VS_FETCH_SOLID(1,  0,  1,17)
    VS_FETCH_SOLID(-1,  1, -1,18)  VS_FETCH_SOLID(0,  1, -1,19)  VS_FETCH_SOLID(1,  1, -1,20)
    VS_FETCH_SOLID(-1,  1,  0,21)  VS_FETCH_SOLID(0,  1,  0,22)  VS_FETCH_SOLID(1,  1,  0,23)
    VS_FETCH_SOLID(-1,  1,  1,24)  VS_FETCH_SOLID(0,  1,  1,25)  VS_FETCH_SOLID(1,  1,  1,26)
    return ret;
}

uint[27] vs_fetchLight3x3x3(uint sectionOffset, ivec3 blockInSectionPos, uint solidMask) {
    uint[27] lights;
    #define VS_FETCH_LIGHT(_x, _y, _z, i) { \
        uvec2 light = vs_lightAt(sectionOffset, uvec3(blockInSectionPos + ivec3(_x, _y, _z))); \
        lights[i] = (light.x) | ((light.y) << 10u) | (uint((solidMask & (1u << uint(i))) == 0u) << 20u); \
    }
    VS_FETCH_LIGHT(-1, -1, -1, 0)  VS_FETCH_LIGHT(0, -1, -1, 1)  VS_FETCH_LIGHT(1, -1, -1, 2)
    VS_FETCH_LIGHT(-1, -1,  0, 3)  VS_FETCH_LIGHT(0, -1,  0, 4)  VS_FETCH_LIGHT(1, -1,  0, 5)
    VS_FETCH_LIGHT(-1, -1,  1, 6)  VS_FETCH_LIGHT(0, -1,  1, 7)  VS_FETCH_LIGHT(1, -1,  1, 8)
    VS_FETCH_LIGHT(-1,  0, -1, 9)  VS_FETCH_LIGHT(0,  0, -1,10)  VS_FETCH_LIGHT(1,  0, -1,11)
    VS_FETCH_LIGHT(-1,  0,  0,12)  VS_FETCH_LIGHT(0,  0,  0,13)  VS_FETCH_LIGHT(1,  0,  0,14)
    VS_FETCH_LIGHT(-1,  0,  1,15)  VS_FETCH_LIGHT(0,  0,  1,16)  VS_FETCH_LIGHT(1,  0,  1,17)
    VS_FETCH_LIGHT(-1,  1, -1,18)  VS_FETCH_LIGHT(0,  1, -1,19)  VS_FETCH_LIGHT(1,  1, -1,20)
    VS_FETCH_LIGHT(-1,  1,  0,21)  VS_FETCH_LIGHT(0,  1,  0,22)  VS_FETCH_LIGHT(1,  1,  0,23)
    VS_FETCH_LIGHT(-1,  1,  1,24)  VS_FETCH_LIGHT(0,  1,  1,25)  VS_FETCH_LIGHT(1,  1,  1,26)
    return lights;
}

#define vs_index3x3x3(x, y, z) ((x) + (z) * 3u + (y) * 9u)
#define vs_validCountToAo(validCount) (1.0 - (4.0 - (validCount)) * 0.2)

vec3 vs_lightForDirection(uint[27] lights, vec3 interpolant,
                          uint c00, uint c01, uint c10, uint c11,
                          uint oppositeMask) {
    uint[8] summed;
    #define VS_SUM_CORNER(_x, _y, _z, i) { \
        uint corner = vs_index3x3x3(_x, _y, _z); \
        summed[i] = lights[c00 + corner] + lights[c01 + corner] + lights[c10 + corner] + lights[c11 + corner]; \
    }
    VS_SUM_CORNER(0u, 0u, 0u, 0)
    VS_SUM_CORNER(1u, 0u, 0u, 1)
    VS_SUM_CORNER(0u, 0u, 1u, 2)
    VS_SUM_CORNER(1u, 0u, 1u, 3)
    VS_SUM_CORNER(0u, 1u, 0u, 4)
    VS_SUM_CORNER(1u, 1u, 0u, 5)
    VS_SUM_CORNER(0u, 1u, 1u, 6)
    VS_SUM_CORNER(1u, 1u, 1u, 7)

    vec3[8] adjusted;
    // Inner-face correction: if a corner has zero valid blocks, pull from the
    // opposite corner via the bit-flip given by oppositeMask. uint() casts so
    // the ternary branches are both uint (strict GLSL refuses int^uint).
    #define VS_CORNER_INDEX(i) ((summed[uint(i)] & VS_UPPER_10_BITS) == 0u ? uint(i) ^ oppositeMask : uint(i))

    const float[5] normalizers = float[](0.0, 1.0, 1.0/2.0, 1.0/3.0, 1.0/4.0);

    #define VS_ADJUST_CORNER(i) { \
        uint corner = summed[VS_CORNER_INDEX(i)]; \
        uint validCount = corner >> 20u; \
        adjusted[i].xy = vec2(corner & VS_LOWER_10_BITS, (corner >> 10u) & VS_LOWER_10_BITS) * normalizers[validCount]; \
        adjusted[i].z = float(validCount); \
    }
    VS_ADJUST_CORNER(0) VS_ADJUST_CORNER(1) VS_ADJUST_CORNER(2) VS_ADJUST_CORNER(3)
    VS_ADJUST_CORNER(4) VS_ADJUST_CORNER(5) VS_ADJUST_CORNER(6) VS_ADJUST_CORNER(7)

    vec3 light00 = mix(adjusted[0], adjusted[1], interpolant.x);
    vec3 light01 = mix(adjusted[2], adjusted[3], interpolant.x);
    vec3 light10 = mix(adjusted[4], adjusted[5], interpolant.x);
    vec3 light11 = mix(adjusted[6], adjusted[7], interpolant.x);
    vec3 light0 = mix(light00, light01, interpolant.z);
    vec3 light1 = mix(light10, light11, interpolant.z);
    vec3 light = mix(light0, light1, interpolant.y);

    light.xy = clamp(light.xy * VS_LIGHT_NORMALIZER, VS_UV_MIN, VS_UV_MAX);
    light.z = vs_validCountToAo(light.z);
    return light;
}

struct VsLightAo {
    vec2 light;
    float ao;
};

// Single-block world-light lookup at worldPos. Used as a fallback so we can
// still get the correct sky-light (e.g. 0 in a cave) when the smooth lookup
// can't run — without this, callers fall back to the shipyard's baked
// sky-light, which is ~max because the shipyard is an open-sky void.
bool vs_lightFlat(vec3 worldPos, out vec2 light) {
    ivec3 blockPos = ivec3(floor(worldPos));
    uint sectionIndex;
    if (vs_chunkCoordToSectionIndex(blockPos >> 4, sectionIndex)) {
        return false;
    }
    uint sectionOffset = sectionIndex * VS_SECTION_SIZE_INTS;
    ivec3 blockInSectionPos = (blockPos & 0xF) + 1;
    uvec2 raw = vs_lightAt(sectionOffset, uvec3(blockInSectionPos));
    light = clamp(vec2(raw) * VS_LIGHT_NORMALIZER, VS_UV_MIN, VS_UV_MAX);
    return true;
}

bool vs_lightSmooth(vec3 worldPos, vec3 normal, out VsLightAo lightAoOut) {
    ivec3 blockPos = ivec3(floor(worldPos));
    uint lightSectionIndex;
    if (vs_chunkCoordToSectionIndex(blockPos >> 4, lightSectionIndex)) {
        return false;
    }
    uint sectionOffset = lightSectionIndex * VS_SECTION_SIZE_INTS;
    ivec3 blockInSectionPos = (blockPos & 0xF) + 1;

    uint solid = vs_fetchSolid3x3x3(sectionOffset, blockInSectionPos);
    if (solid == VS_COMPLETELY_SOLID) {
        lightAoOut.light = vec2(VS_UV_MIN);
        lightAoOut.ao = vs_validCountToAo(0.0);
        return true;
    }
    uint[27] lights = vs_fetchLight3x3x3(sectionOffset, blockInSectionPos, solid);
    vec3 interpolant = fract(worldPos);

    vec3 lightX;
    if (normal.x > VS_EPSILON) {
        lightX = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(1u, 0u, 0u), vs_index3x3x3(1u, 0u, 1u),
            vs_index3x3x3(1u, 1u, 0u), vs_index3x3x3(1u, 1u, 1u), 1u);
    } else if (normal.x < -VS_EPSILON) {
        lightX = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(0u, 0u, 0u), vs_index3x3x3(0u, 0u, 1u),
            vs_index3x3x3(0u, 1u, 0u), vs_index3x3x3(0u, 1u, 1u), 1u);
    } else {
        lightX = vec3(0.0);
    }

    vec3 lightZ;
    if (normal.z > VS_EPSILON) {
        lightZ = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(0u, 0u, 1u), vs_index3x3x3(0u, 1u, 1u),
            vs_index3x3x3(1u, 0u, 1u), vs_index3x3x3(1u, 1u, 1u), 2u);
    } else if (normal.z < -VS_EPSILON) {
        lightZ = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(0u, 0u, 0u), vs_index3x3x3(0u, 1u, 0u),
            vs_index3x3x3(1u, 0u, 0u), vs_index3x3x3(1u, 1u, 0u), 2u);
    } else {
        lightZ = vec3(0.0);
    }

    vec3 lightY;
    if (normal.y > VS_EPSILON) {
        lightY = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(0u, 1u, 0u), vs_index3x3x3(0u, 1u, 1u),
            vs_index3x3x3(1u, 1u, 0u), vs_index3x3x3(1u, 1u, 1u), 4u);
    } else if (normal.y < -VS_EPSILON) {
        lightY = vs_lightForDirection(lights, interpolant,
            vs_index3x3x3(0u, 0u, 0u), vs_index3x3x3(0u, 0u, 1u),
            vs_index3x3x3(1u, 0u, 0u), vs_index3x3x3(1u, 0u, 1u), 4u);
    } else {
        lightY = vec3(0.0);
    }

    vec3 n2 = normal * normal;
    vec3 lightAo = lightX * n2.x + lightY * n2.y + lightZ * n2.z;
    lightAoOut.light = lightAo.xy;
    lightAoOut.ao = lightAo.z;
    return true;
}
#endif // VS_DYNAMIC_LIGHT

#ifdef VS_SHIP_ON_SHIP
// ===== Ship-on-ship: distance-attenuated emitter list ====================
// Loop bounds for the per-fragment scans. Should be ≤ the corresponding
// MAX_* constants in the Java lists; 128 covers most real ship setups.
const int VS_SOS_EMITTER_LOOP_CAP = 128;
const int VS_SOS_OCCLUDER_LOOP_CAP = 128;

// Max distance-attenuated contribution from any ship emitter (incl. own
// ship) at this fragment's world position. Manhattan falloff is taken in
// the emitter's owning-ship frame so the octahedral light bubble rotates
// with the hull.
float vs_sosEmitterLight(vec3 worldPos) {
    float maxLight = 0.0;
    int n = min(u_VsShipEmitterCount, VS_SOS_EMITTER_LOOP_CAP);
    for (int i = 0; i < n; i++) {
        vec4 e = texelFetch(u_VsShipEmitters, i * 2);
        vec4 q = texelFetch(u_VsShipEmitters, i * 2 + 1);
        vec3 offset_ship = vs_sosQuatRotateInv(q, worldPos - e.xyz);
        float dist = abs(offset_ship.x) + abs(offset_ship.y) + abs(offset_ship.z);
        float light = max(0.0, e.w - dist);
        maxLight = max(maxLight, light);
    }
    return maxLight;
}

// Per-fragment ship-to-ship AO. Mirrors the world-FSH ws_shipAo: each
// solid ship voxel projects an octagonal Manhattan tent on the face,
// computed in the voxel's owning-ship local frame so the shadow rotates
// with the hull. Diagonal corner cells get the bilinear-vs-Manhattan
// extra (cornerExtra) gated by ≥2 contributors so isolated and adjacent
// voxels keep their clean octagonal shadow but X-X gaps and rows fill
// in to vanilla brightness.
float vs_sosShipAo(vec3 worldPosWorld, vec3 nf) {
    int n = min(u_VsShipOccluderCount, VS_SOS_OCCLUDER_LOOP_CAP);

    float occlusionManhattan = 0.0;
    float occlusionCorner = 0.0;
    int cornerContributors = 0;

    for (int i = 0; i < n; i++) {
        vec4 voxel = texelFetch(u_VsShipOccluders, i * 2);
        vec4 q = texelFetch(u_VsShipOccluders, i * 2 + 1);

        vec3 d_world = voxel.xyz - worldPosWorld;
        vec3 d_ship = vs_sosQuatRotateInv(q, d_world);
        vec3 nf_ship = vs_sosQuatRotateInv(q, nf);

        float d_n = dot(d_ship, nf_ship);
        if (d_n <= 0.0 || d_n >= 1.5) continue;
        float fn = 1.0 - smoothstep(0.5, 1.5, d_n);

        vec3 helper = abs(nf_ship.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0);
        vec3 uAxis = normalize(cross(helper, nf_ship));
        vec3 vAxis = cross(nf_ship, uAxis);
        float du = dot(d_ship, uAxis);
        float dv = dot(d_ship, vAxis);

        float dU = abs(du) - 0.5;
        float dV = abs(dv) - 0.5;
        float manhattan = max(0.0, 1.0 - max(dU, 0.0) - max(dV, 0.0));

        float fU = clamp(1.0 - dU, 0.0, 1.0);
        float fV = clamp(1.0 - dV, 0.0, 1.0);
        float cornerExtra = max(0.0, fU * fV - manhattan);

        occlusionManhattan += (1.0 / 3.0) * fn * manhattan;
        float contribC = (1.0 / 3.0) * fn * cornerExtra;
        occlusionCorner += contribC;
        if (contribC > 0.0) {
            cornerContributors++;
        }
    }

    float occlusion = occlusionManhattan
            + (cornerContributors >= 2 ? occlusionCorner : 0.0);
    occlusion = clamp(occlusion, 0.0, 1.0);
    return mix(0.2, 1.0, 1.0 - occlusion);
}
#endif // VS_SHIP_ON_SHIP

// (No biome helpers in the FSH — biome lookup happens per-vertex in the VSH
// and arrives via the v_VertexBiomeTint varying. The FSH just multiplies it.)

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

#ifdef USE_VANILLA_COLOR_FORMAT
    diffuseColor *= v_Color;
#else
#if defined(VS_DYNAMIC_LIGHT) || defined(VS_DYNAMIC_SHADE) || defined(VS_SHIP_ON_SHIP)
    // World-space normal: the VSH packs the per-quad face direction into the
    // alpha byte, decodes it to a shipyard-space normal, and transforms by
    // u_TransformMatrix. v_WorldNormal is `flat`-interpolated so all 4 quad
    // vertices contribute the same value — exact for a flat quad and free of
    // the dFdx/dFdy precision artifacts on small triangles.
    vec3 worldN = v_WorldNormal;
#endif

    // Fullbright / emissive: the mesher mixin checks BakedQuad.getVertices()
    // at the LIGHT_INDEX offset and packs the FULLBRIGHT face slot if non-zero
    // (i.e. the source model JSON tagged the quad emissive). v_IsFullbright is
    // the decoded flag — no fragment-time heuristic.
    bool isFullbright = v_IsFullbright != 0;
    bool isShade = v_IsShaded != 0;

    vec2 lightCoord;
    float aoMultiplier;
#ifdef VS_DYNAMIC_LIGHT
    // Absolute world position of the fragment (camera-relative + integer origin).
    vec3 worldPos = v_CameraRelWorldPos + vec3(u_VsRenderOrigin);
    // Default-init so the compiler can prove the values are defined when we
    // skip the smooth-fetch branch (vs_lightSmooth doesn't write its out param
    // on early-return paths).
    VsLightAo vsLight;
    vsLight.light = vec2(0.0);
    vsLight.ao = 1.0;
    if (isFullbright) {
        // Skip the world-light lookup entirely; fullbright = max lightmap, no
        // AO, no directional shade.
        lightCoord = vec2(VS_UV_MAX);
        aoMultiplier = 1.0;
    } else if (vs_lightSmooth(worldPos, worldN, vsLight)) {
        // World-space lighting + AO at the ship's rendered location.
        // Block-light: max with baked so ship-internal torches still glow
        //   (they live in shipyard, the world engine doesn't see them here).
        // Sky-light: take from world (baked sky is from shipyard, irrelevant
        //   to the ship's actual location).
        // AO: combine world-external AO with the ship-internal AO baked into
        //   v_Color.a by the mesher.
        lightCoord = vec2(
            max(vsLight.light.x, v_BakedLightCoord.x),
            vsLight.light.y
        );
        aoMultiplier = vsLight.ao * v_Color.a;
    } else {
        // Fallback when the smooth lookup misses (section not tracked yet).
        vec2 flatLight;
        if (vs_lightFlat(worldPos, flatLight)) {
            lightCoord = vec2(
                max(flatLight.x, v_BakedLightCoord.x),
                flatLight.y
            );
        } else {
            lightCoord = clamp(v_BakedLightCoord, VS_UV_MIN, VS_UV_MAX);
        }
        aoMultiplier = v_Color.a;
    }
#else
    // Dynamic lighting disabled: just use the shipyard-baked lightmap. Caves
    // and torches in the world will not affect the ship; the shipyard's open-
    // sky bake means everything looks brightly lit even underground.
    if (isFullbright) {
        lightCoord = vec2(VS_UV_MAX);
        aoMultiplier = 1.0;
    } else {
        lightCoord = clamp(v_BakedLightCoord, VS_UV_MIN, VS_UV_MAX);
        aoMultiplier = v_Color.a;
    }
#endif

    // BlockColors-baked vertex tint multiplied by the rasterizer-blended
    // world-biome tint sampled per-vertex in the VSH. v_VertexBiomeTint is
    // vec3(1.0) for non-biome quads, so this is a no-op there. For biome-
    // tinted quads the mesher mixin already white-d out v_Color.rgb so the
    // multiply yields just the world-biome color smoothly blended across the
    // quad's vertices.
    vec3 vertTint = v_Color.rgb * v_VertexBiomeTint;

#ifdef VS_SHIP_ON_SHIP
    // Ship-on-ship: the world-from-ship storage holds every ship's voxels
    // projected into world coords (and dilated emitter values). Reading it at
    // this fragment's world block lets nearby ships shadow / illuminate this
    // ship's surface. Skipped for fullbright quads (already at max lightmap)
    // and reuses the same world-position varyings as VS_DYNAMIC_LIGHT.
    float sosShipAo = 1.0;
    if (!isFullbright) {
        // Ship emitters anywhere (own ship + other ships). The emitter list
        // stores world-space FLOAT coords, so as a ship slides sub-block the
        // distance from each fragment varies continuously — the lit area
        // tracks ship motion without any block-grid quantization.
        vec3 sosWorldPos = v_CameraRelWorldPos + vec3(u_VsRenderOrigin);
        float sosLight = vs_sosEmitterLight(sosWorldPos);
        if (sosLight > 0.0) {
            lightCoord.x = max(lightCoord.x, (sosLight + 0.5) / 16.0);
        }

        // Ship-to-ship AO: own ship's voxels casting shadows on this ship's
        // concave faces, plus any other ship's voxels that happen to be
        // adjacent in world space. Both run through the same SDF, in each
        // contributing voxel's ship-frame, so shadows track each hull's
        // rotation independently.
        sosShipAo = vs_sosShipAo(sosWorldPos, worldN);
    }
#endif

    vec4 lightSample = texture(u_LightTex, lightCoord);
    diffuseColor.rgb *= vertTint * lightSample.rgb;

#ifdef VS_SHIP_ON_SHIP
    // Stack the ship-to-ship AO with the existing aoMultiplier additively
    // (in occlusion-loss space) — same compounding rule the world FSH uses
    // for vanilla×ship AO so two op cells at a corner sum to loss 0.4
    // instead of 0.64. Floor at 0.2 matches sodium's deepest opaque AO.
    aoMultiplier = max(0.2, aoMultiplier - (1.0 - sosShipAo));
#endif

#ifdef VS_DYNAMIC_SHADE
    // Directional shade (vanilla "side darkening"): applied only when the quad
    // opted in (BakedQuad.isShade()) AND we didn't detect fullbright above.
    // aoMultiplier is already 1.0 for fullbright so the else-branch is a no-op
    // there too. Disabled: just AO-darken; ship sides won't have the vanilla
    // direction-based brightness drop.
    if (isShade && !isFullbright) {
        diffuseColor.rgb *= vanillaShadeFromNormal(worldN) * aoMultiplier;
    } else {
        diffuseColor.rgb *= aoMultiplier;
    }
#else
    diffuseColor.rgb *= aoMultiplier;
#endif
#endif

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}
