#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 vs_LocalToCameraRel;

uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 ChunkOffset;
uniform vec3 vs_MaterialOrigin;
out vec3 vs_ShipPos;
uniform int FogShape;

uniform vec3 u_VsCameraFrac;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

out vec3 v_CameraRelWorldPos;   // worldPos - floor(camera); + u_VsRenderOrigin == abs world pos
flat out float v_IsShaded;    // 1 for shaded faces.
out vec2 v_BakedLightCoord;     // ship-internal baked lightmap UV (from UV2)
flat out float v_Fullbright;    // 1.0 if the quad was baked emissive (UV2 == FULL_BRIGHT)

#moj_import <vs_batched_ship_vertex.glsl>

void main() {
    vs_shipVertex(ModelViewMat, vs_LocalToCameraRel);
}
