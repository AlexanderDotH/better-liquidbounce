#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;

const vec2 DIRECTIONS[8] = vec2[](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 0.70710678,  0.70710678), vec2(-0.70710678,  0.70710678),
    vec2( 0.70710678, -0.70710678), vec2(-0.70710678, -0.70710678)
);

void main() {
    vec4 center = texture(MaskSampler, texCoord);
    vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));
    vec4 nearest = vec4(0.0);

    for (int i = 0; i < 8; ++i) {
        vec4 inner = texture(MaskSampler, texCoord + DIRECTIONS[i] * texel);
        vec4 outer = texture(MaskSampler, texCoord + DIRECTIONS[i] * texel * 2.0);
        if (inner.a > nearest.a) nearest = inner;
        if (outer.a > nearest.a) nearest = outer;
    }

    float edge = smoothstep(0.015, 0.85, nearest.a - center.a) * 0.95;
    vec3 color = nearest.a > 0.0001 ? nearest.rgb / nearest.a : vec3(0.0);
    fragColor = vec4(color * edge, edge);
}
