void vs_shipVertex(mat4 modelView, mat4 localToCameraRel) {
    vs_ShipPos = Position + vs_MaterialOrigin;
    vec3 pos = Position + ChunkOffset;
    vec4 currentClip = ProjMat * modelView * vec4(pos, 1.0);
#ifdef VS_SHIP_MOTION_VECTORS
    vec4 previousClip = ProjMat * vs_shipPreviousModelView() * vec4(pos, 1.0);
    v_VsMotion = previousClip.w > 0.05
        ? currentClip.xy / currentClip.w - previousClip.xy / previousClip.w
        : vec2(0.0);
#endif
    gl_Position = currentClip;

    vertexDistance = fog_distance(modelView, pos, FogShape);
    texCoord0 = UV0;

    v_IsShaded = Color.a == 0.0 ? 0.0 : 1.0;

    v_CameraRelWorldPos = (localToCameraRel * vec4(pos, 1.0)).xyz;

    v_BakedLightCoord = clamp(vec2(UV2) / 256.0, vec2(1.0 / 32.0), vec2(31.0 / 32.0));
    v_Fullbright = (UV2.x >= 240 && UV2.y >= 240) ? 1.0 : 0.0;

    if (Color.a == 0.0) {
        vertexColor = vec4(Color.rgb, 1.0);
    } else {
        vertexColor = vec4(Color.rgb, Color.a);
    }
}
