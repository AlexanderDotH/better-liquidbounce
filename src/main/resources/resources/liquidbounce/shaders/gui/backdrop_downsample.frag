#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SceneSampler;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(SceneSampler, 0));
    vec2 offset = texel * 0.5;
    vec3 color = 0.25 * (
        texture(SceneSampler, texCoord + vec2(-offset.x, -offset.y)).rgb +
        texture(SceneSampler, texCoord + vec2( offset.x, -offset.y)).rgb +
        texture(SceneSampler, texCoord + vec2(-offset.x,  offset.y)).rgb +
        texture(SceneSampler, texCoord + vec2( offset.x,  offset.y)).rgb
    );
    fragColor = vec4(color, 1.0);
}
