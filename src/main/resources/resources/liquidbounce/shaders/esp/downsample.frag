#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;

vec4 premultiply(vec4 color) {
    return vec4(color.rgb * color.a, color.a);
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));
    vec2 offset = texel * 0.5;
    fragColor = 0.25 * (
        premultiply(texture(MaskSampler, texCoord + vec2(-offset.x, -offset.y))) +
        premultiply(texture(MaskSampler, texCoord + vec2( offset.x, -offset.y))) +
        premultiply(texture(MaskSampler, texCoord + vec2(-offset.x,  offset.y))) +
        premultiply(texture(MaskSampler, texCoord + vec2( offset.x,  offset.y)))
    );
}
