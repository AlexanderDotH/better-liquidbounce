#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DepthSampler;
uniform sampler2D DhDepthSampler;

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

const int SOURCE_SKY = 0;
const int SOURCE_DH = 1;
const int SOURCE_VANILLA = 2;

bool isDepthClear(float depth, vec4 sourceInfo) {
    float clearEpsilon = max(sourceInfo.w, 1.0e-7);
    return sourceInfo.z <= 0.5 || abs(depth - sourceInfo.x) <= clearEpsilon;
}

int classifyTerrainSource(float vanillaDepth, float dhDepth) {
    bool vanillaTerrain = !isDepthClear(vanillaDepth, VanillaDepthInfo);
    bool dhTerrain = !isDepthClear(dhDepth, DhDepthInfo);

    if (vanillaTerrain) return SOURCE_VANILLA;
    if (dhTerrain) return SOURCE_DH;
    return SOURCE_SKY;
}

void main() {
    float vanillaDepth = texture(DepthSampler, texCoord).r;
    float dhDepth = texture(DhDepthSampler, texCoord).r;
    int source = classifyTerrainSource(vanillaDepth, dhDepth);
    float terrainCoverage = source == SOURCE_SKY ? 0.0 : 1.0;

    // Only the exact binary red channel is consumed. The source channel is useful
    // for diagnostics and records that Vanilla won an overlap classification.
    fragColor = vec4(terrainCoverage, float(source) * 0.5, 0.0, 1.0);
}
