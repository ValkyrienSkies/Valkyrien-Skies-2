#include "flywheel:internal/common.frag"

#if defined(FLW_EMBEDDED) || defined(_FLW_CRUMBLING)
uniform int vs_PiecePlaneCount;
uniform vec4 vs_FirstPiecePlane;
uniform samplerBuffer vs_PiecePlanes;

void vs_pieceMain() {
    vec4 position = vec4(flw_vertexPos.xyz, 1.0);
    if (vs_PiecePlaneCount > 0 && dot(vs_FirstPiecePlane, position) < 0.0) discard;
    for (int i = 1; i < vs_PiecePlaneCount; ++i) {
        if (dot(texelFetch(vs_PiecePlanes, i - 1), position) < 0.0) discard;
    }
    _flw_main();
}
#define _flw_main vs_pieceMain
#endif
