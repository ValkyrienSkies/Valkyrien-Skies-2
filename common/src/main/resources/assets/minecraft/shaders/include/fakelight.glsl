#version 150

#define MINECRAFT_LIGHT_X   (0.6)
#define MINECRAFT_LIGHT_Z   (0.8)
#define MINECRAFT_LIGHT_Y   (0.5)

float vanillaShadeFromNormal(vec3 n) {
    vec3 an = abs(n);

    float yShade = n.y > 0.0 ? 1.0 : MINECRAFT_LIGHT_Y;

    float shade =
    an.x * MINECRAFT_LIGHT_X +
    an.z * MINECRAFT_LIGHT_Z +
    an.y * yShade;

    return shade / (an.x + an.y + an.z);
}
