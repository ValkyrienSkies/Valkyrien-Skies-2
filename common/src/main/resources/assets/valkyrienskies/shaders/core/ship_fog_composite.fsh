#version 150

uniform sampler2D CompositeSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(CompositeSampler, texCoord);
}
