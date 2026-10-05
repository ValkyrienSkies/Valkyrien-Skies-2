#version 150

#moj_import <light.glsl>
#moj_import <fakelight.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

out vec3 valkyrienair_CamRelPos;
out vec2 v_VsLightCoordRaw;
out vec3 v_VsWorldNormal;

void main() {
    vec3 pos = Position + ChunkOffset;
    vec4 viewPos = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * viewPos;

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
    valkyrienair_CamRelPos = IViewRotMat * viewPos.xyz;
    v_VsLightCoordRaw = clamp(vec2(UV2) / 256.0, vec2(1.0 / 32.0), vec2(31.0 / 32.0));

    vec3 worldNormal = normalize(IViewRotMat * mat3(ModelViewMat) * Normal);
    v_VsWorldNormal = worldNormal;

    if (Color.a == 0.0) {
        vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
        vertexColor.a = 1.0;
    } else {
        vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
        float shade = vanillaShadeFromNormal(worldNormal);
        vertexColor.rgb *= shade;
    }
}
