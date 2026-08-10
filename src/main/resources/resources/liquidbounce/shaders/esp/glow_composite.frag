#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D MaskSampler;
uniform sampler2D BlurSampler;
uniform sampler2D CoreExclusionSampler;

layout(std140) uniform EspStyleData {
    vec4 glowParams;
    vec4 outlineParams;
};

const vec2 DIRECTIONS[8] = vec2[](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 0.70710678,  0.70710678), vec2(-0.70710678,  0.70710678),
    vec2( 0.70710678, -0.70710678), vec2(-0.70710678, -0.70710678)
);

vec3 straightColor(vec4 premultiplied) {
    return premultiplied.a > 0.0001 ? premultiplied.rgb / premultiplied.a : vec3(0.0);
}

void main() {
    vec4 center = texture(MaskSampler, texCoord);
    vec4 blurred = texture(BlurSampler, texCoord);
    vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));

    vec4 nearest = vec4(0.0);
    for (int i = 0; i < 8; ++i) {
        vec2 sampleCoord = texCoord + DIRECTIONS[i] * texel * glowParams.x;
        vec4 candidate = texture(MaskSampler, sampleCoord);
        float exclusion = texture(CoreExclusionSampler, sampleCoord).a * glowParams.w;
        candidate *= 1.0 - smoothstep(0.02, 0.65, exclusion);
        if (candidate.a > nearest.a) nearest = candidate;
    }

    float protectedSurface = texture(CoreExclusionSampler, texCoord).a;
    float outside = 1.0 - smoothstep(0.02, 0.65, max(center.a, protectedSurface));
    float haloAlpha = clamp(min(0.72, blurred.a * 1.18) * glowParams.y * glowParams.z * outside, 0.0, 1.0);
    float coreAlpha = smoothstep(0.02, 0.92, nearest.a - center.a) * 0.95 * glowParams.z * outside;

    vec4 halo = vec4(straightColor(blurred) * haloAlpha, haloAlpha);
    vec3 coreColor = nearest.a > 0.0001 ? nearest.rgb / nearest.a : straightColor(blurred);
    vec4 core = vec4(coreColor * coreAlpha, coreAlpha);

    fragColor = core + halo * (1.0 - core.a);
}
