uniform samplerBuffer u_VsShipShadowBoxes;
uniform int u_VsShipShadowCount;
uniform float u_VsShipShadowStrength;
uniform float u_VsShipShadowSoftness;
uniform float u_VsShipShadowBlurGrowth;

vec3 vs_shadowRotateInverse(vec4 rotation, vec3 value) {
    vec3 axis = -rotation.xyz;
    return value + 2.0 * cross(axis, cross(axis, value) + rotation.w * value);
}

float vs_shipSkyVisibility(vec3 worldPos, vec3 worldNormal) {
    float occlusion = 0.0;
    float baseSoftness = max(u_VsShipShadowSoftness, 0.0);
    float blurGrowth = max(u_VsShipShadowBlurGrowth, 0.0);
    float pixelWidth = max(length(dFdx(worldPos)), length(dFdy(worldPos)));
    for (int i = 0; i < min(u_VsShipShadowCount, 512); i++) {
        vec3 center = texelFetch(u_VsShipShadowBoxes, i * 3).xyz;
        vec3 halfSize = texelFetch(u_VsShipShadowBoxes, i * 3 + 1).xyz;
        float radius = length(halfSize);
        vec3 offset = worldPos - center;
        float maxSoftness = min(8.0, baseSoftness + blurGrowth * length(offset));
        if (baseSoftness > 0.0 || blurGrowth > 0.0) maxSoftness = max(maxSoftness, pixelWidth);
        if (abs(offset.x) > radius + maxSoftness || abs(offset.z) > radius + maxSoftness
            || offset.y > radius + 0.35) continue;
        vec4 rotation = texelFetch(u_VsShipShadowBoxes, i * 3 + 2);
        vec3 origin = vs_shadowRotateInverse(rotation, offset);
        vec3 direction = vs_shadowRotateInverse(rotation, vec3(0.0, 1.0, 0.0));
        vec3 surfaceOffset = sign(origin) * max(abs(origin) - halfSize, vec3(0.0));
        float surfaceDistance = length(surfaceOffset);
        float softness = min(8.0, baseSoftness + blurGrowth * surfaceDistance);
        if (baseSoftness > 0.0 || blurGrowth > 0.0) softness = max(softness, pixelWidth);
        if (softness < 0.00001) {
            float enter = 0.02;
            float leave = 100000.0;
            bool missed = false;
            for (int axis = 0; axis < 3; axis++) {
                if (abs(direction[axis]) < 0.000001) {
                    if (abs(origin[axis]) > halfSize[axis]) missed = true;
                } else {
                    float first = (-halfSize[axis] - origin[axis]) / direction[axis];
                    float last = (halfSize[axis] - origin[axis]) / direction[axis];
                    enter = max(enter, min(first, last));
                    leave = min(leave, max(first, last));
                }
            }
            if (!missed && enter < leave) return 0.0;
            continue;
        }
        // Each box axis defines an edge of its projected outline.
        float edgeDistance = -100000.0;
        for (int axis = 0; axis < 3; axis++) {
            vec3 boxAxis = vec3(0.0);
            boxAxis[axis] = 1.0;
            vec3 edgeNormal = cross(direction, boxAxis);
            float normalLength = length(edgeNormal);
            if (normalLength < 0.000001) continue;
            edgeNormal /= normalLength;
            float edge = abs(dot(origin, edgeNormal)) - dot(halfSize, abs(edgeNormal));
            edgeDistance = max(edgeDistance, edge);
        }
        float heightAboveSurface = -dot(surfaceOffset, direction);
        float coverage = (1.0 - smoothstep(0.0, softness, edgeDistance))
            * smoothstep(0.0, softness, heightAboveSurface);
        // Near a box, use the sky fraction above its nearest surface.
        // This keeps a turning face from switching at the upward ray limit.
        float contactBlend = 1.0 - smoothstep(0.08, 0.35, surfaceDistance);
        float contactCoverage = 1.0;
        if (surfaceDistance > 0.000001) {
            float surfaceUp = dot(surfaceOffset / surfaceDistance, direction);
            contactCoverage = 0.5 - 0.5 * surfaceUp;
        }
        coverage = mix(coverage, contactCoverage, contactBlend);
        vec3 localNormal = vs_shadowRotateInverse(rotation, worldNormal);
        for (int axis = 0; axis < 3; axis++) {
            float side = sign(origin[axis]);
            float gap = abs(origin[axis]) - halfSize[axis];
            float planeWeight = smoothstep(0.99, 0.9999, side * localNormal[axis])
                * smoothstep(0.0, 0.02, gap) * (1.0 - smoothstep(0.08, 0.35, gap));
            float planeCoverage = 0.5 - 0.5 * side * direction[axis];
            coverage = min(coverage, mix(1.0, planeCoverage, planeWeight));
        }
        occlusion = max(occlusion, coverage);
        if (occlusion >= 0.9999) return 0.0;
    }
    return 1.0 - occlusion;
}

vec4 vs_shipShadowLight(sampler2D lightmap, vec2 lightCoord, vec3 worldPos, vec3 worldNormal) {
    vec4 skyLight = texture(lightmap, lightCoord);
    float shadow = (1.0 - vs_shipSkyVisibility(worldPos, worldNormal)) * clamp(u_VsShipShadowStrength, 0.0, 1.0);
    if (shadow <= 0.0) return skyLight;
    vec4 blockLight = texture(lightmap, vec2(lightCoord.x, 1.0 / 32.0));
    return mix(skyLight, blockLight, shadow);
}
