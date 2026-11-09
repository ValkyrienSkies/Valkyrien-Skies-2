#version 330 core

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>
#import <sodium:include/chunk_material.glsl>

// Embeddium-on-forge flavor of the world chunk VSH. Embeddium doesn't
// expose u_TexCoordShrink or _vert_tex_diffuse_coord_bias (those are
// sodium-fabric-specific atlas-bleed mitigation), so we use the bare
// _vert_tex_diffuse_coord directly. The fabric copy of this shader does
// the texture-coord shrink dance.

uniform int u_FogShape;
uniform vec3 u_RegionOffset;
// Fractional part of the camera's world position (each component in [0, 1)).
// Sodium's vertex `position` is camera-relative using the EXACT camera, so
// we shift it back by frac(camera) to get vertex - floor(camera). Without
// this, floor() at the fragment shifts by ±1 as the camera drifts through
// integer block boundaries — producing visible "jumping" of the occlusion.
uniform vec3 u_VsCameraFrac;

out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_LightCoord;
// Camera-relative world position. FSH does
// `worldBlock = ivec3(floor(v_CameraRelWorldPos)) + u_VsRenderOrigin`.
out vec3 v_CameraRelWorldPos;
// World-space face normal decoded from the alpha-byte face slot. Flat-
// interpolated since all 4 vertices of a quad share the same face.
flat out vec3 v_WorldNormal;
// 1 when the source quad opted into directional shade and AO; 0 for
// fluids (slot 6, FACE_UNSHADED) and emissive/fullbright quads (slot 7).
flat out int v_IsShaded;

out float v_MaterialMipBias;
#ifdef USE_FRAGMENT_DISCARD
out float v_MaterialAlphaCutoff;
#endif

#ifdef USE_FOG
out float v_FragDistance;
#endif

uvec3 _get_relative_chunk_coord(uint pos) {
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

vec3 vs_faceSlotToWorldNormal(uint slot) {
    if (slot == 0u) return vec3(0.0, -1.0, 0.0);
    if (slot == 1u) return vec3(0.0,  1.0, 0.0);
    if (slot == 2u) return vec3(0.0,  0.0, -1.0);
    if (slot == 3u) return vec3(0.0,  0.0,  1.0);
    if (slot == 4u) return vec3(-1.0, 0.0, 0.0);
    if (slot == 5u) return vec3( 1.0, 0.0, 0.0);
    return vec3(0.0, 1.0, 0.0);
}

void main() {
    _vert_init();

    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

#ifdef USE_FOG
    v_FragDistance = getFragDistance(u_FogShape, position);
#endif

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    v_CameraRelWorldPos = position + u_VsCameraFrac;

    uint aoByte = uint(_vert_color.a * 255.0 + 0.5);
    uint aoLevel = aoByte & 7u;
    uint faceSlot = (aoByte >> 3u) & 7u;
    float aoFloat = float(aoLevel) * 0.2;
    v_Color = vec4(_vert_color.rgb, aoFloat);
    v_WorldNormal = vs_faceSlotToWorldNormal(faceSlot);
    v_IsShaded = (faceSlot < 6u) ? 1 : 0;

    // Sodium's _vert_tex_light_coord is an ivec2 in [0, 255] (the raw byte
    // pair from the chunk vertex). Sodium's stock _sample_lightmap divides
    // by 256 before sampling u_LightTex; we sample per-fragment in the FSH,
    // so do the divide here once. Without this, the FSH samples u_LightTex
    // at clamped (~31/32, ~31/32) for every fragment and the world
    // renders uniformly maxed out — vanilla world-to-world torch falloff
    // disappears.
    v_LightCoord = vec2(_vert_tex_light_coord) / 256.0;
    v_TexCoord = _vert_tex_diffuse_coord;

    v_MaterialMipBias = _material_mip_bias(_material_params);
#ifdef USE_FRAGMENT_DISCARD
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
#endif
}
