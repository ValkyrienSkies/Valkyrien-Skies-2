
uniform samplerBuffer u_VsShipEmitters;   // texel 2i = vec4(worldXYZ, lightLevel); 2i+1 = rotation quat (unused here)
uniform int u_VsShipEmitterCount;
const int VS_GLOW_EMITTER_CAP = 128;       // per-fragment loop bound (keep <= VsShipEmitterList.MAX_EMITTERS)

float vs_shipGlowSmooth(vec3 worldPos, vec3 faceNormal) {
    int n = min(u_VsShipEmitterCount, VS_GLOW_EMITTER_CAP);
    float best = 0.0;
    for (int i = 0; i < n; i++) {
        vec4 e = texelFetch(u_VsShipEmitters, i * 2);  // xyz = world pos, w = light level
        best = max(best, e.w - distance(worldPos, e.xyz));
    }
    return best;
}
