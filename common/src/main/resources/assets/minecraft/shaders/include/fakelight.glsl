#version 150

#define MINECRAFT_LIGHT_X   (0.6)
#define MINECRAFT_LIGHT_Z   (0.8)
#define MINECRAFT_LIGHT_Y   (0.5)

uniform float u_VsShipShadeStrength;

float vanillaShadeFromNormal(vec3 n) {
    vec3 weights = n * n;
    float up = max(n.y, 0.0);
    float down = min(n.y, 0.0);
    float shade = weights.x * MINECRAFT_LIGHT_X + weights.z * MINECRAFT_LIGHT_Z
        + up * up + down * down * MINECRAFT_LIGHT_Y;
    return mix(1.0, shade / max(dot(weights, vec3(1.0)), 0.000001),
        clamp(u_VsShipShadeStrength, 0.0, 1.0));
}
