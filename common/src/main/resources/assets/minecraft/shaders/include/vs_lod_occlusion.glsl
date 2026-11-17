uniform int u_VsLodOcclusion;      // 0 when no LOD terrain was drawn this frame
uniform sampler2D u_VsLodDepth;    // LOD depth buffer
uniform vec2 u_VsLodViewport;      // size of the framebuffer ships are drawn into, in pixels
uniform vec4 u_VsLodDepthParams;   // x: A, y: B (distance = B / (ndc + A)), z: ndc scale, w: ndc bias
uniform float u_VsLodEmptyDepth;   // depth of pixels without LOD terrain

bool vs_behindLodTerrain(vec3 viewPos) {
    if (u_VsLodOcclusion == 0) return false;
    ivec2 size = textureSize(u_VsLodDepth, 0);
    ivec2 texel = clamp(ivec2(gl_FragCoord.xy * vec2(size) / u_VsLodViewport), ivec2(0), size - ivec2(1));
    float depth = texelFetch(u_VsLodDepth, texel, 0).r;
    if (abs(depth - u_VsLodEmptyDepth) < 1.0e-7) return false;
    float ndc = depth * u_VsLodDepthParams.z + u_VsLodDepthParams.w;
    float lodDistance = u_VsLodDepthParams.y / (ndc + u_VsLodDepthParams.x);
    if (!(lodDistance > 0.0)) return false;
    return -viewPos.z > lodDistance + 1.5 + lodDistance * 0.004;
}
