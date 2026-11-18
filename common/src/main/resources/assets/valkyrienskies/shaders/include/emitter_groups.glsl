// Group bounds follow the 1024 unchanged emitter entries in the same buffer.
// Return -1 outside all bounds, 1 for group scans, or 0 for the flat scan.
int vs_emitterBoundsMode(samplerBuffer emitters, vec3 worldPos, int count) {
    if (textureSize(emitters) < 2082) return 0;
    vec4 lower = texelFetch(emitters, 2080);
    if (lower.w == 0.0) return 0;
    vec4 upper = texelFetch(emitters, 2081);
    if (upper.w != float(count)) return 0;
    if (any(lessThan(worldPos, lower.xyz)) || any(greaterThan(worldPos, upper.xyz))) return -1;
    return lower.w > 1.5 ? 1 : 0;
}

bool vs_emitterGroupOutside(samplerBuffer emitters, vec3 worldPos, int group) {
    vec4 lower = texelFetch(emitters, 2048 + group * 2);
    if (lower.w == 0.0) return false;
    vec3 upper = texelFetch(emitters, 2049 + group * 2).xyz;
    return any(lessThan(worldPos, lower.xyz)) || any(greaterThan(worldPos, upper));
}
