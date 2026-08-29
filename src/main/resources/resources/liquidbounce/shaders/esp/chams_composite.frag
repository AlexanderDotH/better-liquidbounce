#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;

layout(std140) uniform EspChamsData {
    vec4 chamsParams;
};

void main() {
    vec4 mask = texture(MaskSampler, texCoord);
    float alpha = mask.a * chamsParams.x;
    vec3 color = mask.a > 0.0001 ? mask.rgb / mask.a : vec3(0.0);
    fragColor = vec4(color * alpha, alpha);
}
