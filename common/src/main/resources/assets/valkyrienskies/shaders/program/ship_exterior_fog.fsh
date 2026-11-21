#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D InteriorMaskSampler;
uniform vec3 FogColor;
uniform vec2 FogParams;
uniform float SkyFogStrength;
uniform float WaterLevel;
uniform mat4 InverseProjMat;
uniform mat4 InverseViewMat;
uniform vec3 CameraWorldPos;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructWorldPos(float depth) {
    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InverseProjMat * clipPos;
    viewPos /= viewPos.w;
    // modelViewMatrix at this stage is rotation + bob-view + damage-tilt, with no world translation
    // (world geometry is rendered camera-relative). Inverting gets us a camera-relative world pos
    // that correctly undoes the bob / tilt offset; add CameraWorldPos to land in actual world space.
    vec3 cameraRelative = (InverseViewMat * vec4(viewPos.xyz, 1.0)).xyz;
    return CameraWorldPos + cameraRelative;
}

// Length of segment AB that lies below y = WaterLevel.
float submergedLength(vec3 a, vec3 b) {
    bool aUnder = a.y < WaterLevel;
    bool bUnder = b.y < WaterLevel;
    if (!aUnder && !bUnder) return 0.0;
    float fullLen = length(b - a);
    if (aUnder && bUnder) return fullLen;
    float t = clamp((WaterLevel - a.y) / (b.y - a.y), 0.0, 1.0);
    return aUnder ? t * fullLen : (1.0 - t) * fullLen;
}

void main() {
    vec4 sceneColor = texture(DiffuseSampler, texCoord);
    float sceneDepth = texture(SceneDepthSampler, texCoord).r;
    // Binary signal from the interior-mask pass: 1.0 if the scene fragment's voxel is inside some
    // ship's air pocket (direct mask lookup at the scene world position), 0.0 otherwise.
    float sceneInPocket = texture(InteriorMaskSampler, texCoord).r;

    if (sceneDepth >= 1.0) {
        // Sky: same fog integration as regular pixels — fog amount is driven by the submerged length
        // of the view ray, not a binary "camera underwater" flag. That way dipping just under the
        // surface doesn't slam the entire sky to the fog color; only the actual water between the
        // camera and the surface contributes.
        vec3 skyPoint = reconstructWorldPos(0.99999);
        float wetDistance = submergedLength(CameraWorldPos, skyPoint);
        float fogDistance = max(0.0, wetDistance - FogParams.y);
        float skyFog = (1.0 - exp(-FogParams.x * fogDistance)) * clamp(SkyFogStrength, 0.0, 1.0);
        fragColor = vec4(mix(sceneColor.rgb, FogColor, skyFog), 1.0);
        return;
    }

    // Scene fragment lives inside a ship's air pocket — don't touch the pixel.
    if (sceneInPocket > 0.5) {
        fragColor = sceneColor;
        return;
    }

    // Fog amount comes from the length of the camera→scene ray that lies below the waterline.
    vec3 sceneWorldPos = reconstructWorldPos(sceneDepth);
    float wetDistance = submergedLength(CameraWorldPos, sceneWorldPos);
    float fogDistance = max(0.0, wetDistance - FogParams.y);
    float fogAmount = 1.0 - exp(-FogParams.x * fogDistance);

    vec3 foggedColor = mix(sceneColor.rgb, FogColor, fogAmount);
    fragColor = vec4(foggedColor, 1.0);
}
