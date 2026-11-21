#include "flywheel:internal/common.vert"

#if defined(FLW_EMBEDDED) || defined(_FLW_CRUMBLING)
uniform bool vs_pieceActive;
uniform mat4 vs_piecePose;
uniform mat3 vs_pieceNormal;

void vs_pieceMain(in FlwInstance instance, in uint stableInstanceID, in uint modelID) {
#ifdef FLW_EMBEDDED
    if (vs_pieceActive) {
        _flw_modelMatrix = vs_piecePose;
        _flw_normalMatrix = vs_pieceNormal;
    }
#endif
    _flw_main(instance, stableInstanceID, modelID);
#if defined(_FLW_CRUMBLING) && !defined(FLW_EMBEDDED)
    if (vs_pieceActive) {
        flw_vertexPos = vs_piecePose * flw_vertexPos;
        flw_vertexNormal = normalize(vs_pieceNormal * flw_vertexNormal);
        flw_distance = fogDistance(flw_vertexPos.xyz, flw_cameraPos, flw_fogShape);
        gl_Position = flw_viewProjection * flw_vertexPos;
    }
#endif
}
#define _flw_main vs_pieceMain
#endif
