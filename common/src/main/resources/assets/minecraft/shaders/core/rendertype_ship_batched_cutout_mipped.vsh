#version 330 core

#moj_import <fakelight.glsl>
#moj_import <fog.glsl>
#moj_import <vs_dynamic_light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform samplerBuffer ShipTransforms;
uniform int ShipIndex;

uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

uniform ivec3 u_VsRenderOrigin;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

out vec3 v_CameraRelWorldPos;   // worldPos - floor(camera); + u_VsRenderOrigin == abs world pos
flat out vec3 v_WorldNormal;    // tilt-correct world-space face normal
out vec2 v_WorldLightCoord;
flat out float v_Fullbright;    // 1.0 if the quad was baked emissive (UV2 == FULL_BRIGHT)

mat4 vs_shipModelView() {
    int b = ShipIndex * 8;
    return mat4(
        texelFetch(ShipTransforms, b + 0),
        texelFetch(ShipTransforms, b + 1),
        texelFetch(ShipTransforms, b + 2),
        texelFetch(ShipTransforms, b + 3));
}

mat4 vs_shipLocalToCameraRel() {
    int b = ShipIndex * 8 + 4;
    return mat4(
        texelFetch(ShipTransforms, b + 0),
        texelFetch(ShipTransforms, b + 1),
        texelFetch(ShipTransforms, b + 2),
        texelFetch(ShipTransforms, b + 3));
}

void main() {
    mat4 modelView = vs_shipModelView();
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * modelView * vec4(pos, 1.0);

    vertexDistance = fog_distance(modelView, pos, FogShape);
    texCoord0 = UV0;

    vec3 worldNormal = normalize(mat3(vs_shipLocalToCameraRel()) * Normal);
    v_WorldNormal = worldNormal;

    v_CameraRelWorldPos = (vs_shipLocalToCameraRel() * vec4(pos, 1.0)).xyz;

    vec2 bakedLight = clamp(vec2(UV2) / 256.0, vec2(VS_UV_MIN), vec2(VS_UV_MAX));
    v_Fullbright = (UV2.x >= 240 && UV2.y >= 240) ? 1.0 : 0.0;

    // Sample smooth world lighting per vertex, then interpolate across the block face.
    // Ship glow remains per fragment so small moving light sources retain their shape.
    vec3 relPos = v_CameraRelWorldPos - worldNormal * 0.02;
    vec3 worldPos = vec3(ivec3(floor(relPos)) + u_VsRenderOrigin) + fract(relPos);
    VsLightAo worldLight;
    if (v_Fullbright > 0.5) {
        v_WorldLightCoord = vec2(VS_UV_MAX);
    } else if (vs_lightSmooth(worldPos, worldNormal, worldLight)) {
        v_WorldLightCoord = vec2(max(worldLight.light.x, bakedLight.x), worldLight.light.y);
    } else {
        v_WorldLightCoord = bakedLight;
    }

    if (Color.a == 0.0) {
        vertexColor = vec4(Color.rgb, 1.0);
    } else {
        vertexColor = vec4(Color.rgb * vanillaShadeFromNormal(worldNormal), Color.a);
    }
}
