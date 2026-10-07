#version 330 core
#import <valkyrienskies:include/ship_shadows.glsl>

#import <sodium:include/fog.glsl>

// Apply ship light and sky shadows to world blocks.
// Shadow boxes follow the ship transform and keep hull openings clear.
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

// Per-frame list of solid ship voxel CENTERS in world space, paired with
// each voxel's owning-ship rotation quaternion. Two RGBA32F texels per
// voxel:
//   texel 2i:   vec4(worldX, worldY, worldZ, 0)
//   texel 2i+1: vec4(qx, qy, qz, qw)   ship-to-world rotation
// The shader applies the inverse rotation to the fragment-to-voxel
// offset so the Manhattan SDF runs in the voxel's ship-local frame.
// That makes each voxel's octagonal shadow rotate with its ship instead
// of staying world-axis-aligned.
uniform samplerBuffer u_VsShipOccluders;
uniform int u_VsShipOccluderCount;

// Inverse-rotate v by quaternion q (i.e., apply q^-1 = (-q.xyz, q.w) to v).
// Used to express world-frame offsets in the owning ship's local frame so
// the SDF / distance metrics line up with the ship's axes.
vec3 vs_quatRotateInv(vec4 q, vec3 v) {
    vec3 qNeg = -q.xyz;
    return v + 2.0 * cross(qNeg, cross(qNeg, v) + q.w * v);
}

out vec4 fragColor;

const float WS_UV_MIN = 1.0 / 32.0;
const float WS_UV_MAX = 31.0 / 32.0;

// Loop bound for the per-fragment ship-occluder scan. Should match
// VsShipOccluderList.MAX_OCCLUDERS — 1024 fits but is excessive per
// fragment; 128 covers typical scenes (a single mid-size ship's solid
// voxels), excess entries beyond this cap are silently ignored.
const int VS_OCCLUDER_LOOP_CAP = 128;

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
//     to one side don't shadow this fragment.
// Smooth falloff in both directions; sum contributions, clamp to 1.
//
// This replaces the cell-storage-based AO that operated on grid-aligned
// world cells — that approach quantized voxel positions to cells and
// the AO pattern could only morph between cell-aligned configs. With
// the voxel list, every voxel's exact transformed position contributes,
// so the AO shape rotates and translates continuously with the ship.
float ws_shipAo(vec3 worldPosWorld, vec3 nf) {
    int n = min(u_VsShipOccluderCount, VS_OCCLUDER_LOOP_CAP);

    float occlusionManhattan = 0.0;
    float occlusionCorner = 0.0;
    int cornerContributors = 0;

    for (int i = 0; i < n; i++) {
        // Two texels per voxel: position (with payload in .w) and the
        // owning ship's rotation quaternion. Apply q^-1 to the
        // fragment-to-voxel offset and to the world face normal to get
        // both into the voxel's ship-local frame; pick face-local U/V
        // from the rotated normal so the SDF axes track the ship.
        vec4 voxel = texelFetch(u_VsShipOccluders, i * 2);
        vec4 q = texelFetch(u_VsShipOccluders, i * 2 + 1);

        vec3 d_world = voxel.xyz - worldPosWorld;
        vec3 d_ship = vs_quatRotateInv(q, d_world);
        vec3 nf_ship = vs_quatRotateInv(q, nf);

        float d_n = dot(d_ship, nf_ship);
        if (d_n <= 0.0 || d_n >= 1.5) continue;
        float fn = 1.0 - smoothstep(0.5, 1.5, d_n);

        // Build a stable orthonormal face basis from the SHIP-frame normal.
        // The old threshold picker could choose the same axis for U and V
        // when nf_ship was diagonal in X/Z, collapsing the footprint into a
        // one-dimensional strip and producing very long shadows.
        vec3 helper = abs(nf_ship.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0);
        vec3 uAxis = normalize(cross(helper, nf_ship));
        vec3 vAxis = cross(nf_ship, uAxis);
        float du = dot(d_ship, uAxis);
        float dv = dot(d_ship, vAxis);

        // Manhattan-distance SDF of the voxel's face-plane box. Reproduces
        // vanilla MC's exact AO shape for an isolated occluder:
        //   - inside the voxel's 1×1 footprint: full 1/3 contribution.
        //   - axially adjacent cells: linear 1/3 → 0 ramp over 1 cell.
        //   - diagonally adjacent cells: triangular falloff cut by the
        //     45° Manhattan iso-line — vanilla's clean-triangle corner.
        //
        // halfSize 0.5: voxel is a unit cell on the face plane. tent
        // reaches 0 at distance 1 cell from the box (matching vanilla's
        // 1-cell AO reach). Voxel position is continuous, so the shape
        // translates AND rotates with voxels — no per-cell
        // decomposition, no world-grid anchoring.
        float dU = abs(du) - 0.5;
        float dV = abs(dv) - 0.5;
        float manhattan = max(0.0, 1.0 - max(dU, 0.0) - max(dV, 0.0));

        // Diagonal corner-cell extra for the "X X" case (two voxels
        // with a 1-cell gap between them). Manhattan alone falls to 0
        // along |Δu|+|Δv|=1, so each X contributes 0 at the gap-front
        // fragment (dU=0.5, dV=0.5 from each), leaving a bright wedge
        // where vanilla has continuous AO. The corner-extra term
        // promotes the tent to bilinear (1−dU)(1−dV) inside the
        // diagonal cell, filling the gap to 1/12 per voxel. Factors
        // clamp at 0/1 so distant voxels contribute nothing.
        //
        // Gated below by `cornerContributors >= 2`: the corner cell is
        // filled only when at least two voxels are themselves landing
        // a cornerExtra contribution at this fragment — the X-X-gap
        // signature. An isolated voxel triggers at most one corner
        // contributor (its own diagonal cell) so the fill is dropped
        // and the clean Manhattan octagon is preserved. Adjacent voxels
        // (XX, no gap) also drop the fill: only the diagonal voxel of
        // the pair has cornerExtra > 0; the axial voxel's contribution
        // goes to Manhattan, doesn't bump cornerContributors, so the
        // single corner contributor isn't enough to fill.
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

    // Ship emitters: max-merge their distance-attenuated contribution into the
    // block-light UV. Sub-block-precise because the emitter coords are floats.
    float shipLight = vs_shipEmitterLight(worldPos);
    if (shipLight > 0.0) {
        // MC packs block-light at U = (lightLevel + 0.5) / 16.
        float shipLightUv = (shipLight + 0.5) / 16.0;
        lightCoord.x = max(lightCoord.x, shipLightUv);
    }

    vec4 lightSample = vs_shipShadowLight(u_LightTex,
        clamp(lightCoord, vec2(WS_UV_MIN), vec2(WS_UV_MAX)), worldPos + v_WorldNormal * 0.04, v_WorldNormal);

    // Tint × lightmap. AO and shade are applied below as a single combined
    // multiplier so vanilla world AO and ship-to-world AO stack the way
    // vanilla's per-vertex averaging would, instead of multiplying
    // independently.
    diffuseColor.rgb *= v_Color.rgb * lightSample.rgb;

    // Combined AO + shade. v_Color.a is PURE vanilla AO (no shade); ship
    // AO comes from per-fragment ws_shipAo() with dynamic triangle split.
    // Combine the two AO sources additively — vanilla's per-vertex
    // averaging compounds overlap that way (two op cells at a corner →
    // loss 0.2 + 0.2 = 0.4, not 0.8 × 0.8 = 0.64). Floor 0.2 matches
    // sodium's deepest AO value for opaque blocks. Face shade (UP=1,
    // DOWN=0.5, N/S=0.8, E/W=0.6) is applied after, mirroring sodium's
    // applySidedBrightness step. Slot 6/7 (unshaded/fullbright) skip
    // both AO and shade via v_IsShaded.
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
    // {
    //     // DEBUG: red = ship AO loss; green = vanilla AO loss.
    //     float dbgVanillaLoss = clamp((1.0 - v_Color.a) * 1.25, 0.0, 1.0);
    //     float dbgShipLoss = clamp((1.0 - shipAo) * 1.25, 0.0, 1.0);
    //     diffuseColor.rgb = vec3(dbgShipLoss, dbgVanillaLoss, 0.0)
    //             + lightSample.rgb * 1e-3;
    // }

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}
