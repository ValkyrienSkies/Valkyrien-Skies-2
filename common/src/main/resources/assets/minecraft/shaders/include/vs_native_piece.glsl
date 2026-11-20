uniform int vs_PiecePlaneCount;
uniform vec4 vs_FirstPiecePlane;
uniform samplerBuffer vs_PiecePlanes;
in vec3 vs_PiecePos;

void vs_clipNativePiece() {
    vec4 pos = vec4(vs_PiecePos, 1.0);
    if (vs_PiecePlaneCount > 0 && dot(vs_FirstPiecePlane, pos) < 0.0) discard;
    for (int i = 1; i < vs_PiecePlaneCount; ++i) {
        if (dot(texelFetch(vs_PiecePlanes, i - 1), pos) < 0.0) discard;
    }
}
