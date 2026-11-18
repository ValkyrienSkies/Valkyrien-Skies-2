#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform samplerBuffer ShipTransforms;
uniform int ShipIndex;

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
noperspective out vec2 v_VsMotion;

out vec3 v_CameraRelWorldPos;   // worldPos - floor(camera); + u_VsRenderOrigin == abs world pos
flat out float v_IsShaded;    // 1 for shaded faces.
out vec2 v_BakedLightCoord;     // ship-internal baked lightmap UV (from UV2)
flat out float v_Fullbright;    // 1.0 if the quad was baked emissive (UV2 == FULL_BRIGHT)

mat4 vs_shipModelView() {
    int b = ShipIndex * 12;
    return mat4(
        texelFetch(ShipTransforms, b + 0),
        texelFetch(ShipTransforms, b + 1),
        texelFetch(ShipTransforms, b + 2),
        texelFetch(ShipTransforms, b + 3));
}

mat4 vs_shipPreviousModelView() {
    int b = ShipIndex * 12 + 8;
    return mat4(
        texelFetch(ShipTransforms, b + 0),
        texelFetch(ShipTransforms, b + 1),
        texelFetch(ShipTransforms, b + 2),
        texelFetch(ShipTransforms, b + 3));
}

mat4 vs_shipLocalToCameraRel() {
    int b = ShipIndex * 12 + 4;
    return mat4(
        texelFetch(ShipTransforms, b + 0),
        texelFetch(ShipTransforms, b + 1),
        texelFetch(ShipTransforms, b + 2),
        texelFetch(ShipTransforms, b + 3));
}

#define VS_SHIP_MOTION_VECTORS
#moj_import <vs_batched_ship_vertex.glsl>

void main() {
    vs_shipVertex(vs_shipModelView(), vs_shipLocalToCameraRel());
}
