uniform samplerBuffer u_VsShipEmitters;
uniform int u_VsShipEmitterCount;
uniform int u_VsShipEmitterIndices[128];

float vs_shipGlowSmooth(vec3 worldPos, vec3 faceNormal) {
    float best = 0.0;
    for (int i = 0; i < u_VsShipEmitterCount; i++) {
        vec4 emitter = texelFetch(u_VsShipEmitters, u_VsShipEmitterIndices[i] * 2);
        vec3 delta = worldPos - emitter.xyz;
        float distanceSquared = dot(delta, delta);
        float remaining = emitter.w - best;
        if (remaining > 0.0 && distanceSquared < remaining * remaining) {
            best = emitter.w - sqrt(distanceSquared);
        }
    }
    return best;
}
