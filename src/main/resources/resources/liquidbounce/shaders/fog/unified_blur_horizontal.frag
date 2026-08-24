#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D FogSampler;
uniform sampler2D TerrainMaskSampler;

layout(std140) uniform UnifiedFogData {
    mat4 InverseProjection;
    mat4 InverseViewRotation;
    mat4 DhInverseMvmProjection;
    vec4 FogColor;
    vec4 HorizonInfo;
    vec4 CameraPositionAndTime;
    vec4 VanillaDepthInfo;
    vec4 DhDepthInfo;
    vec4 ViewportInfo;
    vec4 VolumeSettings;
    vec4 LayerSettings;
};

layout(std140) uniform UnifiedFogKernelData {
    vec4 Pair0;
    vec4 Pair1;
    vec4 Pair2;
    vec4 Pair3;
    vec4 Pair4;
    vec4 Pair5;
};

const float EPSILON = 1.0e-6;
const float TERRAIN_THRESHOLD = 0.5;

bool isTerrain(vec2 uv) {
    return texture(TerrainMaskSampler, uv).r >= TERRAIN_THRESHOLD;
}

float centerWeight() {
    float pairedWeight = Pair0.y + Pair1.y + Pair2.y + Pair3.y + Pair4.y + Pair5.y;
    return max(0.0, 1.0 - pairedWeight * 2.0);
}

void addPair(inout vec4 fogSum, inout float totalWeight, vec2 direction, vec4 pairData) {
    if (pairData.y <= 0.0) return;

    vec2 offset = direction * pairData.x;
    for (int side = -1; side <= 1; side += 2) {
        vec2 sampleUv = clamp(texCoord + offset * float(side), vec2(0.0), vec2(1.0));
        float sampleSkyMask = isTerrain(sampleUv) ? 0.0 : 1.0;
        vec4 sampledFog = texture(FogSampler, sampleUv);
        float kernelWeight = pairData.y;
        fogSum += sampledFog * sampleSkyMask * kernelWeight;
        totalWeight += kernelWeight * sampleSkyMask;
    }
}

void main() {
    if (isTerrain(texCoord)) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 direction = vec2(ViewportInfo.z, 0.0);
    float kernelCenterWeight = centerWeight();
    vec4 fogSum = texture(FogSampler, texCoord) * kernelCenterWeight;
    float totalWeight = kernelCenterWeight;
    addPair(fogSum, totalWeight, direction, Pair0);
    addPair(fogSum, totalWeight, direction, Pair1);
    addPair(fogSum, totalWeight, direction, Pair2);
    addPair(fogSum, totalWeight, direction, Pair3);
    addPair(fogSum, totalWeight, direction, Pair4);
    addPair(fogSum, totalWeight, direction, Pair5);
    fragColor = fogSum / max(totalWeight, EPSILON);
}
