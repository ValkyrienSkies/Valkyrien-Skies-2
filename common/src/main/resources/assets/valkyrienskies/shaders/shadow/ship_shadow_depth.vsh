#version 330 core
layout(location = 0) in vec4 Center;
layout(location = 1) in vec4 HalfSize;
layout(location = 2) in vec4 Rotation;
uniform vec3 Cascade;
uniform vec2 Height;
uniform int LocalSpace;
uniform samplerBuffer ShipTransforms;
void main() {
    vec3 center = Center.xyz;
    vec3 extent = vec3(length(HalfSize.xyz));
    mat3 transform;
    if (LocalSpace != 0) {
        int first = int(Center.w) * 3;
        vec4 x = texelFetch(ShipTransforms, first);
        vec4 y = texelFetch(ShipTransforms, first + 1);
        vec4 z = texelFetch(ShipTransforms, first + 2);
        center = vec3(dot(x.xyz, center) + x.w, dot(y.xyz, center) + y.w, dot(z.xyz, center) + z.w);
        extent = vec3(dot(abs(x.xyz), HalfSize.xyz), dot(abs(y.xyz), HalfSize.xyz), dot(abs(z.xyz), HalfSize.xyz));
        transform = transpose(mat3(x.xyz, y.xyz, z.xyz));
    }
    // Reject a whole instance before rasterization when its bounds miss this map.
    if (any(greaterThan(abs(center.xz - Cascade.xy), vec2(Cascade.z) + extent.xz))) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    int corner = gl_VertexID;
    vec3 p = (vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1) * 2.0 - 1.0) * HalfSize.xyz;
    if (LocalSpace != 0) p = transform * p;
    else p += 2.0 * cross(Rotation.xyz, cross(Rotation.xyz, p) + Rotation.w * p);
    p += center;
    gl_Position = vec4((p.xz - Cascade.xy) / Cascade.z,
        (Height.x - p.y) / Height.y * 2.0 - 1.0, 1.0);
}
