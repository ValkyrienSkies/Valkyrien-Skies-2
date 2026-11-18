#moj_import <vs_emitter_groups.glsl>
uniform samplerBuffer u_VsShipEmitters;   // texel 2i = vec4(worldXYZ, lightLevel); 2i+1 = rotation quat (unused here)
uniform int u_VsShipEmitterCount;
const int VS_GLOW_EMITTER_CAP = 128;       // per-fragment loop bound (keep <= VsShipEmitterList.MAX_EMITTERS)

float vs_shipGlowSmooth(vec3 worldPos, vec3 faceNormal) {
    int n = min(u_VsShipEmitterCount, VS_GLOW_EMITTER_CAP);
    float best = 0.0;
    if (n <= 0) return best;
    int mode = vs_emitterBoundsMode(u_VsShipEmitters, worldPos, n);
    if (mode < 0) return best;
    if (mode == 0) {
        for (int i = 0; i < n; i++) {
            vec4 e = texelFetch(u_VsShipEmitters, i * 2);
            best = max(best, e.w - distance(worldPos, e.xyz));
        }
        return best;
    }
    for (int first = 0; first < n; first += 8) {
        if (vs_emitterGroupOutside(u_VsShipEmitters, worldPos, first / 8)) continue;
        for (int i = first; i < min(first + 8, n); i++) {
            vec4 e = texelFetch(u_VsShipEmitters, i * 2);  // xyz = world pos, w = light level
            best = max(best, e.w - distance(worldPos, e.xyz));
        }
    }
    return best;
}
