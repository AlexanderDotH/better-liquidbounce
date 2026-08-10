#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D BlurSampler;
uniform sampler2D MaskSampler;

void main() {
    float maskAlpha = texture(MaskSampler, texCoord).a;
    if (maskAlpha <= 0.001) {
        discard;
    }

    vec3 blurredColor = texture(BlurSampler, texCoord).rgb;
    fragColor = vec4(blurredColor, maskAlpha);
}
