#version 150

out vec2 texCoord;

void main() {
    vec2 position = gl_VertexID == 0 ? vec2(-1.0, -1.0) :
        (gl_VertexID == 1 ? vec2(3.0, -1.0) : vec2(-1.0, 3.0));
    gl_Position = vec4(position, 0.0, 1.0);
    texCoord = position * 0.5 + 0.5;
}
