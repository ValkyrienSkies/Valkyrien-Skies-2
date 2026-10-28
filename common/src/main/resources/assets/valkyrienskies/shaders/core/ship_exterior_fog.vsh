#version 150

in vec3 Position;
out vec2 texCoord;

void main() {
    texCoord = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position, 1.0);
}
