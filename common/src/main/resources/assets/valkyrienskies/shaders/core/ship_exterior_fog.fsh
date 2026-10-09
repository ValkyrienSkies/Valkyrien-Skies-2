#version 150

uniform sampler2D SceneColorSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D InteriorMaskSampler;
uniform vec3 FogColor;
uniform vec2 FogParams;
uniform float ExteriorWaterGate;
uniform vec3 ExteriorWaterGateRow0;
uniform vec3 ExteriorWaterGateRow1;
uniform vec3 ExteriorWaterGateRow2;
uniform float SkyFogStrength;
uniform mat4 InverseProjMat;
uniform vec3 CameraWorldPos;
uniform vec3 CameraLookVector;
uniform vec3 CameraUpVector;
uniform vec3 CameraLeftVector;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructViewPos(float depth) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InverseProjMat * clipPos;
    return viewPos.xyz / viewPos.w;
}

vec3 reconstructWorldPos(float depth) {
    vec3 viewPos = reconstructViewPos(depth);
    return CameraWorldPos
        - CameraLeftVector * viewPos.x
        + CameraUpVector * viewPos.y
        - CameraLookVector * viewPos.z;
}

float sampleExteriorGate(vec2 uv) {
    vec2 clampedUv = clamp(uv, 0.0, 1.0);
    float scaledX = clampedUv.x * 2.0;
    float scaledY = (1.0 - clampedUv.y) * 2.0;

    float fracX = fract(scaledX);
    float fracY = fract(scaledY);
    int cellX = int(floor(scaledX));
    int cellY = int(floor(scaledY));
    cellX = clamp(cellX, 0, 1);
    cellY = clamp(cellY, 0, 1);

    vec3 rowA = cellY == 0 ? ExteriorWaterGateRow0 : ExteriorWaterGateRow1;
    vec3 rowB = cellY == 0 ? ExteriorWaterGateRow1 : ExteriorWaterGateRow2;

    float topA = rowA[cellX];
    float topB = rowA[cellX + 1];
    float bottomA = rowB[cellX];
    float bottomB = rowB[cellX + 1];
    float top = mix(topA, topB, fracX);
    float bottom = mix(bottomA, bottomB, fracX);
    return clamp(mix(top, bottom, fracY), 0.0, 1.0);
}

void main() {
    vec4 sceneColor = texture(SceneColorSampler, texCoord);
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    vec4 interiorMask = texture(InteriorMaskSampler, texCoord);
    float dryFraction = interiorMask.r;
    float exteriorGate = sampleExteriorGate(texCoord);
    float fallbackExteriorGate = clamp(ExteriorWaterGate, 0.0, 1.0);

    if (sceneDepth >= 1.0) {
        vec3 skyWorldPos = reconstructWorldPos(0.99999);
        vec3 skyRayDir = normalize(skyWorldPos - CameraWorldPos);
        float downwardBias = clamp((-skyRayDir.y + 0.15) / 0.5, 0.0, 1.0);
        float skyGate = clamp(max(exteriorGate, fallbackExteriorGate * 0.35) * mix(0.95, 1.0, downwardBias) * SkyFogStrength, 0.0, 1.0);
        vec3 skyFoggedColor = mix(sceneColor.rgb, FogColor, skyGate);
        fragColor = vec4(skyFoggedColor, 1.0);
        return;
    }

    vec3 viewPos = reconstructViewPos(sceneDepth);
    float sceneDistance = length(viewPos);
    float dryDistance = clamp(dryFraction, 0.0, 1.0) * sceneDistance;
    float fogDistance = max(0.0, sceneDistance - dryDistance - FogParams.y);
    float fogAmount = 1.0 - exp(-FogParams.x * fogDistance);
    fogAmount *= max(0.0, 1.0 - clamp(dryFraction, 0.0, 1.0));
    fogAmount *= exteriorGate;
    vec3 foggedColor = mix(sceneColor.rgb, FogColor, fogAmount);
    fragColor = vec4(foggedColor, 1.0);
}
