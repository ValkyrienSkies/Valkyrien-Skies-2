#version 330 core

#import <sodium:include/fog.glsl>

// VS-modified copy of sodium's stock chunk FSH. The only effect added on top of
// vanilla world rendering is "ship lights brighten the world": each ship-emitter
// is fed in as an entry in u_VsShipEmitters (vec4 = worldPos + lightLevel) and
// we max-merge the distance-attenuated contribution into the world's lightmap
// UV. No occlusion (sky shadowing or wall attenuation) — those approximations
// were causing visible artifacts so they're removed.
//
// Sub-block precision: emitter world coords are stored as floats, so as a ship
// moves smoothly the lit area on the ground tracks it continuously. The old
// BFS-over-block-grid approach quantized emitter positions to floor() and made
// the lit region jump per-block.

in vec4 v_Color;            // RGB = chunk-mesher tinted color, .a = pure vanilla AO (no shade)
in vec2 v_TexCoord;
in vec2 v_LightCoord;       // _vert_tex_light_coord (vanilla world lightmap UV)
in vec3 v_CameraRelWorldPos;// world pos relative to camera; + u_VsRenderOrigin = absolute
// Decoded face data from the VSH (see VsVertexFlagPacker). Flat
// interpolated, since face slot is shared by all 4 vertices of a quad.
flat in vec3 v_WorldNormal; // exact world-space face normal, axis-aligned ±X/±Y/±Z
flat in int v_IsShaded;     // 0 for fluids and emissive/fullbright quads
in float v_FragDistance;
in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex;
uniform sampler2D u_LightTex;
uniform vec4 u_FogColor;
uniform float u_FogStart;
uniform float u_FogEnd;

uniform ivec3 u_VsRenderOrigin;
// Buffer texture (RGBA32F) — TWO texels per ship emitter:
//   texel 2i:   vec4(worldX, worldY, worldZ, lightLevel)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation quaternion
// The quaternion's inverse rotates the world-frame fragment-to-emitter
// offset into the emitter's owning ship local frame, so the Manhattan
// light bubble visibly rotates with the hull.
uniform samplerBuffer u_VsShipEmitters;
uniform int u_VsShipEmitterCount;

// Per-frame list of solid ship voxel CENTERS in world space, paired
// with the voxel's owning-ship rotation quaternion. Two RGBA32F
// texels per voxel:
//   texel 2i:   vec4(worldX, worldY, worldZ, shipIndex)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation
// The shader applies the inverse rotation to the fragment-to-voxel
// offset so the polygon test runs in the voxel's ship-local frame —
// each voxel's octagonal shadow rotates with its ship instead of
// staying world-axis-aligned.
uniform samplerBuffer u_VsShipOccluders;
uniform int u_VsShipOccluderCount;

// External-world fluid culling for ship air pockets. These uniforms are populated by
// ShipWaterPocketExternalWaterCull when this shader is used for Sodium's translucent fluid pass.
uniform float ValkyrienAir_CullEnabled;
uniform float ValkyrienAir_IsShipPass;
uniform vec3 ValkyrienAir_CameraWorldPos;
uniform sampler2D ValkyrienAir_FluidMask;

uniform vec4 ValkyrienAir_ShipAabbMin0;
uniform vec4 ValkyrienAir_ShipAabbMax0;
uniform vec4 ValkyrienAir_GridSize0;
uniform mat4 ValkyrienAir_WorldToShip0;
uniform sampler2D ValkyrienAir_Mask0;

uniform vec4 ValkyrienAir_ShipAabbMin1;
uniform vec4 ValkyrienAir_ShipAabbMax1;
uniform vec4 ValkyrienAir_GridSize1;
uniform mat4 ValkyrienAir_WorldToShip1;
uniform sampler2D ValkyrienAir_Mask1;

uniform vec4 ValkyrienAir_ShipAabbMin2;
uniform vec4 ValkyrienAir_ShipAabbMax2;
uniform vec4 ValkyrienAir_GridSize2;
uniform mat4 ValkyrienAir_WorldToShip2;
uniform sampler2D ValkyrienAir_Mask2;

uniform vec4 ValkyrienAir_ShipAabbMin3;
uniform vec4 ValkyrienAir_ShipAabbMax3;
uniform vec4 ValkyrienAir_GridSize3;
uniform mat4 ValkyrienAir_WorldToShip3;
uniform sampler2D ValkyrienAir_Mask3;

uniform vec4 ValkyrienAir_ShipAabbMin4;
uniform vec4 ValkyrienAir_ShipAabbMax4;
uniform vec4 ValkyrienAir_GridSize4;
uniform mat4 ValkyrienAir_WorldToShip4;
uniform sampler2D ValkyrienAir_Mask4;

uniform vec4 ValkyrienAir_ShipAabbMin5;
uniform vec4 ValkyrienAir_ShipAabbMax5;
uniform vec4 ValkyrienAir_GridSize5;
uniform mat4 ValkyrienAir_WorldToShip5;
uniform sampler2D ValkyrienAir_Mask5;

uniform vec4 ValkyrienAir_ShipAabbMin6;
uniform vec4 ValkyrienAir_ShipAabbMax6;
uniform vec4 ValkyrienAir_GridSize6;
uniform mat4 ValkyrienAir_WorldToShip6;
uniform sampler2D ValkyrienAir_Mask6;

uniform vec4 ValkyrienAir_ShipAabbMin7;
uniform vec4 ValkyrienAir_ShipAabbMax7;
uniform vec4 ValkyrienAir_GridSize7;
uniform mat4 ValkyrienAir_WorldToShip7;
uniform sampler2D ValkyrienAir_Mask7;

uniform vec4 ValkyrienAir_ShipAabbMin8;
uniform vec4 ValkyrienAir_ShipAabbMax8;
uniform vec4 ValkyrienAir_GridSize8;
uniform mat4 ValkyrienAir_WorldToShip8;
uniform sampler2D ValkyrienAir_Mask8;
// World-section storage for per-fragment world-voxel scanning inside
// ws_shipAo. Same layout the ship FSH uses (block_layer_opaque.fsh):
// a flat solid-bit + light-byte buffer plus an index LUT. Exposed to
// the world shader so the X-X corner rule can fold in world blocks
// alongside ship voxels — without it, the SDF only sees ship voxels
// (in u_VsShipOccluders) and vanilla baked AO from sodium handles
// the world side, producing two separate shadow shapes that don't
// combine cleanly. Bound from WorldThing.java.
uniform usamplerBuffer u_VsLightSections;
uniform usamplerBuffer u_VsLightLut;

// Inverse-rotate v by quaternion q (i.e., apply q^-1 = (-q.xyz, q.w) to v).
// Used to express world-frame offsets in the owning ship's local frame so
// the SDF / distance metrics line up with the ship's axes.
vec3 vs_quatRotateInv(vec4 q, vec3 v) {
    vec3 qNeg = -q.xyz;
    return v + 2.0 * cross(qNeg, cross(qNeg, v) + q.w * v);
}

// ===== Section-storage helpers (mirrored from ship FSH) =====
// Layout: each section is [solid bits 732B][light bytes 5832B] = 6564B = 1641 ints.
const uint VS_BLOCKS_PER_SECTION = 18u * 18u * 18u;
const uint VS_LIGHT_SIZE_BYTES = VS_BLOCKS_PER_SECTION;
const uint VS_SOLID_SIZE_BYTES = ((VS_BLOCKS_PER_SECTION + 31u) / 32u) * 4u;
const uint VS_SOLID_START_INTS = 0u;
const uint VS_SECTION_SIZE_INTS = (VS_SOLID_SIZE_BYTES + VS_LIGHT_SIZE_BYTES) / 4u;

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

out vec4 fragColor;

const float WS_UV_MIN = 1.0 / 32.0;
const float WS_UV_MAX = 31.0 / 32.0;

// Loop bound for the per-fragment ship-occluder scan. Pair detection is
// CPU-packed into voxel.w, so this no longer does an inner fragment scan.
// 256 keeps cross-ship cases visible without the old 1024x1024 fragment cost.
const int VS_OCCLUDER_LOOP_CAP = 256;

const float VS_AO_CARDINAL_MIN_DISTANCE = 0.75;
const float VS_AO_CARDINAL_MAX_DISTANCE = 2.10;
const float VS_AO_CARDINAL_CROSS_EPSILON = 0.18;
const float VS_AO_MERGE_BAND_PERP = 1.50;
const float VS_AO_MERGE_BAND_EPSILON = 0.03;

bool vs_isFaceTangentCardinalOffset(ivec3 offset, vec3 absNf) {
    if (absNf.x > 0.5) {
        return (offset.y != 0 && offset.z == 0) || (offset.z != 0 && offset.y == 0);
    }
    if (absNf.y > 0.5) {
        return (offset.x != 0 && offset.z == 0) || (offset.z != 0 && offset.x == 0);
    }
    return (offset.x != 0 && offset.y == 0) || (offset.y != 0 && offset.x == 0);
}

bool vs_inFlaggedPairBand(float axisDelta, float perpDelta) {
    return axisDelta >= 0.5 - VS_AO_MERGE_BAND_EPSILON
        && axisDelta <= VS_AO_CARDINAL_MAX_DISTANCE - 0.5 + VS_AO_MERGE_BAND_EPSILON
        && perpDelta <= VS_AO_MERGE_BAND_PERP;
}

bool vs_hasActiveCardinalFlag(vec3 voxelPos, vec3 worldPos, int flags, vec3 absNf) {
    vec3 d = abs(worldPos - voxelPos);
    bool cardX = (flags & 0x10000) != 0;
    bool cardY = (flags & 0x20000) != 0;
    bool cardZ = (flags & 0x40000) != 0;

    if (cardX && absNf.x < 0.5) {
        float perp = absNf.y > 0.5 ? d.z : d.y;
        if (vs_inFlaggedPairBand(d.x, perp)) return true;
    }
    if (cardY && absNf.y < 0.5) {
        float perp = absNf.x > 0.5 ? d.z : d.x;
        if (vs_inFlaggedPairBand(d.y, perp)) return true;
    }
    if (cardZ && absNf.z < 0.5) {
        float perp = absNf.x > 0.5 ? d.y : d.x;
        if (vs_inFlaggedPairBand(d.z, perp)) return true;
    }
    return false;
}
const int VA_MASK_TEX_WIDTH_SHIFT = 12;
const int VA_MASK_TEX_WIDTH_MASK = (1 << VA_MASK_TEX_WIDTH_SHIFT) - 1;
const int VA_SUB = 8;
const int VA_OCC_WORDS_PER_VOXEL = 16;
const float VA_WORLD_SAMPLE_EPS = 0.0001;

bool va_isFluidUv(vec2 uv) {
    return texture(ValkyrienAir_FluidMask, uv).r > 0.5;
}

uint va_fetchWord(sampler2D tex, int wordIndex) {
    ivec2 coord = ivec2(wordIndex & VA_MASK_TEX_WIDTH_MASK, wordIndex >> VA_MASK_TEX_WIDTH_SHIFT);
    vec4 raw = texelFetch(tex, coord, 0) * 255.0;
    uvec4 bytes = uvec4(round(raw));
    return bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u);
}

bool va_testAir(sampler2D mask, int voxelIdx, ivec3 isize) {
    int volume = isize.x * isize.y * isize.z;
    int occBase = volume * VA_OCC_WORDS_PER_VOXEL;
    int wordIndex = occBase + (voxelIdx >> 5);
    int bit = voxelIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_testOcc(sampler2D mask, int voxelIdx, int subIdx) {
    int wordIndex = voxelIdx * VA_OCC_WORDS_PER_VOXEL + (subIdx >> 5);
    int bit = subIdx & 31;
    uint word = va_fetchWord(mask, wordIndex);
    return ((word >> uint(bit)) & 1u) != 0u;
}

bool va_shouldDiscardForShip(vec3 worldPos, vec4 aabbMin, vec4 aabbMax, vec4 gridSize, mat4 worldToShip, sampler2D mask) {
    if (gridSize.x <= 0.0) return false;
    if (worldPos.x < aabbMin.x || worldPos.x > aabbMax.x) return false;
    if (worldPos.y < aabbMin.y || worldPos.y > aabbMax.y) return false;
    if (worldPos.z < aabbMin.z || worldPos.z > aabbMax.z) return false;

    vec3 localPos = (worldToShip * vec4(worldPos, 1.0)).xyz;
    vec3 size = gridSize.xyz;
    if (localPos.x < 0.0 || localPos.y < 0.0 || localPos.z < 0.0) return false;
    if (localPos.x >= size.x || localPos.y >= size.y || localPos.z >= size.z) return false;

    ivec3 v = ivec3(floor(localPos));
    ivec3 isize = ivec3(size);
    int voxelIdx = v.x + isize.x * (v.y + isize.y * v.z);

    ivec3 sv = ivec3(floor(fract(localPos) * float(VA_SUB)));
    sv = clamp(sv, ivec3(0), ivec3(VA_SUB - 1));
    int subIdx = sv.x + VA_SUB * (sv.y + VA_SUB * sv.z);

    if (va_testOcc(mask, voxelIdx, subIdx)) return true;
    if (va_testAir(mask, voxelIdx, isize)) return true;
    return false;
}

bool va_shouldDiscardFluid(vec3 worldPos) {
    return va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin0, ValkyrienAir_ShipAabbMax0, ValkyrienAir_GridSize0, ValkyrienAir_WorldToShip0, ValkyrienAir_Mask0) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin1, ValkyrienAir_ShipAabbMax1, ValkyrienAir_GridSize1, ValkyrienAir_WorldToShip1, ValkyrienAir_Mask1) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin2, ValkyrienAir_ShipAabbMax2, ValkyrienAir_GridSize2, ValkyrienAir_WorldToShip2, ValkyrienAir_Mask2) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin3, ValkyrienAir_ShipAabbMax3, ValkyrienAir_GridSize3, ValkyrienAir_WorldToShip3, ValkyrienAir_Mask3) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin4, ValkyrienAir_ShipAabbMax4, ValkyrienAir_GridSize4, ValkyrienAir_WorldToShip4, ValkyrienAir_Mask4) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin5, ValkyrienAir_ShipAabbMax5, ValkyrienAir_GridSize5, ValkyrienAir_WorldToShip5, ValkyrienAir_Mask5) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin6, ValkyrienAir_ShipAabbMax6, ValkyrienAir_GridSize6, ValkyrienAir_WorldToShip6, ValkyrienAir_Mask6) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin7, ValkyrienAir_ShipAabbMax7, ValkyrienAir_GridSize7, ValkyrienAir_WorldToShip7, ValkyrienAir_Mask7) ||
        va_shouldDiscardForShip(worldPos, ValkyrienAir_ShipAabbMin8, ValkyrienAir_ShipAabbMax8, ValkyrienAir_GridSize8, ValkyrienAir_WorldToShip8, ValkyrienAir_Mask8);
}

// Per-fragment ship AO via voxel-position iteration.
//
// For each ship voxel center (in world coords, stored as a continuous
// float — so the position smoothly tracks the ship's transform including
// rotation), compute its contribution to this fragment's AO based on:
//   • d_n (component along the face normal): how far the voxel is in
//     the outward direction. Voxels in the half-space behind the face
//     (d_n <= 0) are skipped.
//   • d_p (length of the in-plane component): how far the voxel is
//     laterally from the fragment's projected position. Voxels too far
//     to one side don't shadow this fragment.>
// Smooth falloff in both directions; sum contributions, clamp to 1.
//
// This replaces the cell-storage-based AO that operated on grid-aligned
// world cells — that approach quantized voxel positions to cells and
// the AO pattern could only morph between cell-aligned configs. With
// the voxel list, every voxel's exact transformed position contributes,
// so the AO shape rotates and translates continuously with the ship.
float ws_shipAo(vec3 worldPosWorld, vec3 nf) {
    int n = min(u_VsShipOccluderCount, VS_OCCLUDER_LOOP_CAP);

    // Full-block Manhattan AO with multi-ship-aware merging.
    //
    // Per-voxel shape: each ship voxel computes its contribution in
    // its OWNING ship's local frame (the per-voxel quaternion handles
    // that), so its AO shadow rotates with its parent hull. The
    // contribution is a linear ramp from STRENGTH at the NEAREST
    // POINT ON THE 1×1×1 block (zero inside the block) down to 0 at
    // REACH away, using Manhattan distance to the cube itself:
    //   per_axis = max(0, abs(d_ship.axis) - 0.5)
    //   manhattan = per_axis.x + per_axis.y + per_axis.z
    // (still measured in ship-local axes, so axis-aligned w.r.t.
    // that voxel's ship).
    //
    // Merging across voxels: ADDITIVE accumulation, clamped at 1.
    //
    //     occlusion = clamp(STRENGTH * Σ_i contrib_i, 0, 1)
    //
    // Why additive and not multiplicative transmittance: between
    // two blocks one gap apart, each contributes ~0.5 at the
    // midpoint and ~1.0 right next to itself. Beer-Lambert merging
    // gives (1-0.5x)² ≈ 1-x+0.25x² versus (1-x) right next to a
    // block — i.e. the midpoint is brighter than next to either
    // block, so the two shadows look like two blobs with a bright
    // valley between them instead of a single merged dark region.
    // Additive gives 0.5+0.5 = 1.0 at the midpoint, matching
    // adjacent-to-block, so the shadow is flat across the gap and
    // the two blocks' AO merges into one continuous region.
    //
    // This still composes cleanly across ships at different
    // orientations: each voxel's `contrib` is computed in its
    // OWN ship-local frame (so the per-voxel octahedral shadow
    // rotates with its parent hull), and the additive sum is a
    // scalar accumulator in world space — frame- and order-
    // independent. A `+`-rotated voxel (45° yaw) and an axis-
    // aligned `x` voxel sitting side-by-side both deposit their
    // own correctly-oriented contributions into the same scalar,
    // and the shadows merge seamlessly in the middle.
    //
    // The clamp at 1 prevents very dense regions from overflowing,
    // and dense overlap still tops out at the same darkness as a
    // single voxel touching the fragment — which matches vanilla
    // AO's behaviour (vanilla maxes out per-vertex at "fully
    // surrounded" and never goes blacker).
    const float REACH = 1.0;
    const float STRENGTH = 0.25;

    // Vertex-grid AO: vanilla's per-vertex bake puts AO darkness AT
    // face vertices and linearly interpolates across the face quad.
    // For x_x, both gap-side vertices are dark (each adjacent to one
    // block) and the bilinear interp between them paints the gap
    // dark — that's the "merged midline". A per-fragment SDF
    // centered on the voxel produces a small bright spot off-center
    // and never matches that shape, so a world `x` (vanilla per-
    // vertex) plus a ship `+` (per-fragment SDF) showed two
    // disconnected shadows.
    //
    // Fix: evaluate each ship voxel's contribution AT THE 4 FACE
    // VERTICES the fragment sits between, then bilinearly interp
    // those 4 darkness values to the fragment. A ship `+` adjacent
    // to a face vertex now darkens THAT vertex (same as vanilla
    // would for an adjacent solid neighbour), and the bilinear
    // interp paints the merged midline against the world `x`'s
    // dark vertex on the other side.
    vec3 absNf = abs(nf);
    vec3 uAxis = absNf.x > 0.5 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 vAxis = absNf.z > 0.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);

    // Tangent-plane coords of the fragment; vertices live at integer
    // u/v on the world grid. Normal-axis coord stays continuous so
    // the vertex pos lands on the actual face plane the fragment is
    // on (for top face: vertex.y == fragment.y).
    float u_frag = dot(worldPosWorld, uAxis);
    float v_frag = dot(worldPosWorld, vAxis);
    float u_lo = floor(u_frag);
    float v_lo = floor(v_frag);
    float u_t = u_frag - u_lo;
    float v_t = v_frag - v_lo;
    vec3 anchor = uAxis * u_lo + vAxis * v_lo + absNf * dot(worldPosWorld, nf) * sign(dot(nf, vec3(1.0)));
    // sign(dot(nf, 1)) keeps the normal-axis coord positive for the
    // anchor; nf is axis-aligned ±1 so this just picks the right sign.
    vec3 v00 = anchor;
    vec3 v01 = anchor + uAxis;
    vec3 v10 = anchor + vAxis;
    vec3 v11 = anchor + uAxis + vAxis;

    vec4 vertAccum = vec4(0.0); // face-tangent ship-pair merge only
    vec4 shipVertAccum = vec4(0.0); // all ship voxels, used for ship-world merge
    float fragAccum = 0.0;
    float cardinalFragAccum = 0.0;
    int bilinearReachCount = 0;

    for (int i = 0; i < n; i++) {
        vec4 voxel = texelFetch(u_VsShipOccluders, i * 2);
        vec4 q     = texelFetch(u_VsShipOccluders, i * 2 + 1);
        int flags = floatBitsToInt(voxel.w);
        bool hasCardinalCandidate = (flags & 0x70000) != 0;
        bool hasActiveCardinalPair = hasCardinalCandidate
                && vs_hasActiveCardinalFlag(voxel.xyz, worldPosWorld, flags, absNf);

        vec3 nf_ship = vs_quatRotateInv(q, nf);
        // Skip voxels behind the face plane based on fragment
        // anchor — same hemisphere test as before, just done once
        // per voxel instead of per vertex (vertices are within 1
        // cell of the fragment so they share the hemisphere).
        vec3 d_frag_ship = vs_quatRotateInv(q, voxel.xyz - worldPosWorld);
        if (dot(d_frag_ship, nf_ship) <= 0.0) continue;

        vec3 fragDist = max(vec3(0.0), abs(d_frag_ship) - vec3(0.5));
        float fragManhattan = fragDist.x + fragDist.y + fragDist.z;
        float fragContrib = max(0.0, 1.0 - fragManhattan / REACH);
        fragAccum += fragContrib;
        if (hasActiveCardinalPair) {
            cardinalFragAccum += fragContrib;
        }

        // Rotate each of the 4 vertex offsets into ship frame and
        // accumulate its Manhattan tent darkness. Per-vertex (not
        // per-fragment) so a voxel adjacent to v01 fully darkens
        // v01 even when the fragment is at v00; the bilerp at the
        // bottom paints the gap dark from v01 → fragment.
        float b00 = 0.0;
        float b01 = 0.0;
        float b10 = 0.0;
        float b11 = 0.0;
        #define VS_VERT_CONTRIB(totalSlot, voxelSlot, vpos) { \
            vec3 d_v_ship = vs_quatRotateInv(q, voxel.xyz - (vpos)); \
            vec3 vDist = max(vec3(0.0), abs(d_v_ship) - vec3(0.5)); \
            float manhattan = vDist.x + vDist.y + vDist.z; \
            float vertexContrib = max(0.0, 1.0 - manhattan / REACH); \
            totalSlot += vertexContrib; \
            shipVertAccum.voxelSlot += vertexContrib; \
            if (hasActiveCardinalPair) { \
                vertAccum.voxelSlot += vertexContrib; \
            } \
        }
        VS_VERT_CONTRIB(b00, x, v00)
        VS_VERT_CONTRIB(b01, y, v01)
        VS_VERT_CONTRIB(b10, z, v10)
        VS_VERT_CONTRIB(b11, w, v11)
        #undef VS_VERT_CONTRIB
        float voxelBilinearContrib = mix(mix(b00, b01, u_t),
                                         mix(b10, b11, u_t),
                                         v_t);
        if (hasActiveCardinalPair && voxelBilinearContrib > 0.001) {
            bilinearReachCount++;
        }
    }

    // Bilinear interp of vertex accumulators to the fragment. The
    // bilinear path is only used in the merged interior; the outer
    // footprint comes from the per-fragment Manhattan path so the
    // visible corners stay triangular instead of becoming rounded by
    // vertex interpolation.
    float bilinearAccum = mix(mix(vertAccum.x, vertAccum.y, u_t),
                              mix(vertAccum.z, vertAccum.w, u_t),
                              v_t);
    float shipBilinearAccum = mix(mix(shipVertAccum.x, shipVertAccum.y, u_t),
                                  mix(shipVertAccum.z, shipVertAccum.w, u_t),
                                  v_t);

    // Ship-to-world merge: when a ship voxel reaches this
    // fragment (fragAccum > 0), ALSO fold in the surrounding 3×3×3
    // world cells as axis-aligned occluders. The shipAo loss then
    // captures both the ship voxel's own shadow AND the world
    // blocks the ship is "reaching across", which the
    //     combined = ao - (1 - shipAo)
    // formula in main() applies on top of vanilla v_Color.a — the
    // double-count is intentional: it adds the extra darkness the
    // x_+ case needs (ship `+` rotated 45° next to world axis-
    // aligned `x` with a 1-block gap) to look like x_x's merged
    // shadow instead of two disconnected blobs. Vanilla world AO
    // is otherwise preserved, because:
    //   • Pure-world fragments (no ship voxel reaches) skip this
    //     branch entirely — shipAo stays 1.0, combined = ao.
    //   • Ship voxels with no nearby world blocks behave exactly
    //     like before (the inner loop finds nothing solid).
    //
    // Vertex-grid intuition: a face vertex's AO is set by how many
    // adjacent cells are solid. The rotated `+`'s corners poke
    // ~0.207 into the cell adjacent to the gap-vertex, so the
    // vertex grid sees BOTH `x` and `+` as solid neighbours of the
    // gap. Reading the world cells from u_VsLightSections inside
    // the ship-reach gate makes the SDF aware of the same neighbour
    // pair vanilla AO would, so the merge falls out.
    float worldFragAccum = 0.0;
    float worldCardinalFragAccum = 0.0;
    float worldBilinearAccum = 0.0;
    int worldBilinearReachCount = 0;
    if (fragAccum > 0.0) {
        ivec3 worldBlockPos = ivec3(floor(worldPosWorld));
        uint sectionIndex;
        if (!vs_chunkCoordToSectionIndex(worldBlockPos >> 4, sectionIndex)) {
            uint sectionOffset = sectionIndex * VS_SECTION_SIZE_INTS;
            ivec3 blockInSectionPos = (worldBlockPos & 0xF) + 1;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (!vs_isSolid(sectionOffset, uvec3(blockInSectionPos + ivec3(dx, dy, dz)))) continue;
                        vec3 cellCenter = vec3(worldBlockPos + ivec3(dx, dy, dz)) + vec3(0.5);
                        vec3 d = cellCenter - worldPosWorld;
                        if (dot(d, nf) <= 0.0) continue;
                        ivec3 worldOffset = ivec3(dx, dy, dz);
                        bool tangentCardinal = vs_isFaceTangentCardinalOffset(worldOffset, absNf);
                        vec3 vDistW = max(vec3(0.0), abs(d) - vec3(0.5));
                        float manhattanW = vDistW.x + vDistW.y + vDistW.z;
                        float fragContribW = max(0.0, 1.0 - manhattanW / REACH);
                        worldFragAccum += fragContribW;
                        if (tangentCardinal) {
                            worldCardinalFragAccum += fragContribW;
                        }

                        if (!tangentCardinal) continue;

                        float w00 = 0.0;
                        float w01 = 0.0;
                        float w10 = 0.0;
                        float w11 = 0.0;
                        #define VS_WORLD_VERT_SUM(slot, vpos) { \
                            vec3 d_v_world = cellCenter - (vpos); \
                            vec3 vDistWorld = max(vec3(0.0), abs(d_v_world) - vec3(0.5)); \
                            float manhattanWorld = vDistWorld.x + vDistWorld.y + vDistWorld.z; \
                            slot += max(0.0, 1.0 - manhattanWorld / REACH); \
                        }
                        VS_WORLD_VERT_SUM(w00, v00)
                        VS_WORLD_VERT_SUM(w01, v01)
                        VS_WORLD_VERT_SUM(w10, v10)
                        VS_WORLD_VERT_SUM(w11, v11)
                        #undef VS_WORLD_VERT_SUM
                        float worldBilinearContrib = mix(mix(w00, w01, u_t),
                                                         mix(w10, w11, u_t),
                                                         v_t);
                        worldBilinearAccum += worldBilinearContrib;
                        if (worldBilinearContrib > 0.001) {
                            worldBilinearReachCount++;
                        }
                    }
                }
            }
        }
    }

    float accum = fragAccum + worldFragAccum;
    bool hasShipPairMerge = bilinearReachCount >= 2;
    bool hasWorldMerge = worldBilinearReachCount > 0 && shipBilinearAccum > 0.001;
    if (hasShipPairMerge || hasWorldMerge) {
        float mergeShipBilinearAccum = hasWorldMerge ? shipBilinearAccum : bilinearAccum;
        float mergeShipBaseAccum = hasWorldMerge ? fragAccum : cardinalFragAccum;
        float mergeBilinearAccum = mergeShipBilinearAccum + worldBilinearAccum;
        float mergeManhattanAccum = mergeShipBaseAccum + worldCardinalFragAccum;
        accum += max(0.0, mergeBilinearAccum - mergeManhattanAccum);
    }

    float occlusion = clamp(accum * STRENGTH, 0.0, 1.0);
    return mix(0.2, 1.0, 1.0 - occlusion);
}
// Loop bound for the per-fragment emitter scan. Should match
// VsShipEmitterList.MAX_EMITTERS — 1024 entries fit but is excessive per
// fragment; 128 is plenty for typical scenes (ships rarely have that many
// torches in the inner radius). Excess emitters in the buffer beyond this
// cap are silently ignored at fragment time.
const int VS_EMITTER_LOOP_CAP = 128;

// Distance-attenuated max ship-emitter contribution at this fragment's
// world position. Manhattan distance in the emitter's owning-ship frame
// so the octahedral light bubble visibly rotates with the hull.
float vs_shipEmitterLight(vec3 worldPos) {
    float maxLight = 0.0;
    int n = min(u_VsShipEmitterCount, VS_EMITTER_LOOP_CAP);
    for (int i = 0; i < n; i++) {
        vec4 e = texelFetch(u_VsShipEmitters, i * 2);
        vec4 q = texelFetch(u_VsShipEmitters, i * 2 + 1);
        vec3 offset_ship = vs_quatRotateInv(q, worldPos - e.xyz);
        float dist = abs(offset_ship.x) + abs(offset_ship.y) + abs(offset_ship.z);
        float light = max(0.0, e.w - dist);
        maxLight = max(maxLight, light);
    }
    return maxLight;
}

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    vec2 lightCoord = v_LightCoord;
    vec3 worldPos = v_CameraRelWorldPos + vec3(u_VsRenderOrigin);

    if (ValkyrienAir_CullEnabled > 0.5 && ValkyrienAir_IsShipPass < 0.5 && va_isFluidUv(v_TexCoord)) {
        vec3 cullWorldPos = v_CameraRelWorldPos + floor(ValkyrienAir_CameraWorldPos) + vec3(0.0, -VA_WORLD_SAMPLE_EPS, 0.0);
        if (va_shouldDiscardFluid(cullWorldPos)) {
            discard;
        }
    }

    // Ship emitters: max-merge their distance-attenuated contribution into the
    // block-light UV. Sub-block-precise because the emitter coords are floats.
    float shipLight = vs_shipEmitterLight(worldPos);
    if (shipLight > 0.0) {
        // MC packs block-light at U = (lightLevel + 0.5) / 16.
        float shipLightUv = (shipLight + 0.5) / 16.0;
        lightCoord.x = max(lightCoord.x, shipLightUv);
    }

    vec4 lightSample = texture(u_LightTex, clamp(lightCoord, vec2(WS_UV_MIN), vec2(WS_UV_MAX)));

    // Tint × lightmap. AO and shade are applied below as a single combined
    // multiplier so vanilla world AO and ship-to-world AO stack the way
    // vanilla's per-vertex averaging would, instead of multiplying
    // independently.
    diffuseColor.rgb *= v_Color.rgb * lightSample.rgb;

    // Combined AO + shade. v_Color.a is PURE vanilla AO (no shade);
    // ship-to-world AO comes from ws_shipAo() which: (a) for pure-
    // world fragments returns 1.0 (no ship voxels reach → vanilla
    // baked AO is preserved verbatim by the formula below), (b) for
    // fragments where a ship voxel reaches, folds in the 3×3×3
    // world cells around the fragment so the resulting loss
    // includes the merge contribution and stacks darker than the
    // vanilla world AO alone — matching x_x's appearance for the
    // x_+ case. Floor 0.2 matches sodium's deepest opaque AO.
    float ao = v_Color.a;
    float shipAo = (v_IsShaded == 1)
            ? ws_shipAo(v_CameraRelWorldPos + vec3(u_VsRenderOrigin), v_WorldNormal)
            : 1.0;
    if (v_IsShaded == 1) {
        float combined = max(0.2, ao - (1.0 - shipAo));
        float shade = 1.0;
        if (v_WorldNormal.y < -0.5)       shade = 0.5; // DOWN
        else if (abs(v_WorldNormal.y) > 0.5) shade = 1.0; // UP
        else if (abs(v_WorldNormal.x) > 0.5) shade = 0.6; // EAST / WEST
        else                                  shade = 0.8; // NORTH / SOUTH
        diffuseColor.rgb *= combined * shade;
    } else {
        diffuseColor.rgb *= ao;
    }

    // DEBUG: visualize ship AO loss vs vanilla AO loss as separate channels
    // so a real solid block (vanilla AO baked into v_Color.a) and a ship
    // voxel (per-fragment ws_shipAo()) can be placed side-by-side and
    // compared.
    //
    // RED   — ship AO loss (1 - shipAo, scaled).
    // GREEN — vanilla world AO loss (1 - v_Color.a, scaled).
    //
    // If the two formulas produce the same darkening, equivalent setups
    // produce visually identical shapes. Where they disagree, you'll see
    // pure red (ship darker) or pure green (vanilla darker).
    //
    // Tiny +lightSample.rgb keeps u_LightTex alive against GLSL dead-code
    // elimination — without it the compiler strips the texture sample
    // and sodium's bindUniform throws NPE at link time.
    {
        // DEBUG: red = total combined AO loss (ship + vanilla
        // merged via the same formula main rendering uses). V_x,
        // x_x, V_V, V_V_V, x_+ all show at consistent brightness
        // when sodium would treat them equivalently.
        float combinedDbg = (v_IsShaded == 1)
                ? max(0.2, v_Color.a - (1.0 - shipAo))
                : v_Color.a;
        float dbgTotalLoss = clamp((1.0 - combinedDbg) * 1.25, 0.0, 1.0);
        diffuseColor.rgb = vec3(dbgTotalLoss, 0.0, 0.0)
                + lightSample.rgb * 1e-3;
    }

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}
