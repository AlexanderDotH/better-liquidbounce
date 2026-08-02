#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D InputSampler;

layout(std140) uniform EspBlurData {
    vec4 sampleInfo;
    vec4 pair0;
    vec4 pair1;
    vec4 pair2;
    vec4 pair3;
    vec4 pair4;
    vec4 pair5;
};

void addPair(inout vec4 color, vec4 pairData) {
    vec2 offset = sampleInfo.xy * pairData.x;
    color += (
        texture(InputSampler, texCoord + offset) +
        texture(InputSampler, texCoord - offset)
    ) * pairData.y;
}

void main() {
    vec4 color = texture(InputSampler, texCoord) * sampleInfo.z;
    addPair(color, pair0);
    addPair(color, pair1);
    addPair(color, pair2);
    addPair(color, pair3);
    addPair(color, pair4);
    addPair(color, pair5);
    fragColor = color;
}
