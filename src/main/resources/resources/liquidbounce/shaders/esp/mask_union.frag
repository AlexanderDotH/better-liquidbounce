#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;

void main() {
    float alpha = texture(MaskSampler, texCoord).a;
    fragColor = vec4(alpha);
}
