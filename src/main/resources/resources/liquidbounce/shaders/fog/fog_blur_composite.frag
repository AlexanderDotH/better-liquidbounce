#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D BlurSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DhDepthSampler;

layout(std140) uniform FogBlurData {
    mat4 InverseProjection;
    mat4 InverseViewRotation;
    mat4 DhInverseMvmProjection;
    vec4 FogRanges;
    vec4 SampleInfo;
    vec4 DepthInfo;
    vec4 DhDistanceInfo;
    vec4 Pair0;
    vec4 Pair1;
    vec4 Pair2;
    vec4 Pair3;
    vec4 Pair4;
    vec4 Pair5;
};

const int MC_LAYER = 1;
const int FAR_LAYER = 2;
const int DH_LAYER = 3;
const float EPSILON = 1.0e-6;

struct LayerSample {
    int layer;
    float mcDepth;
    float dhDepth;
};

float depthToClip(float depth) {
    return DepthInfo.z > 0.5 ? depth : depth * 2.0 - 1.0;
}

vec3 reconstructRelative(vec2 uv, float depth) {
    vec4 viewPosition = InverseProjection * vec4(uv * 2.0 - 1.0, depthToClip(depth), 1.0);
    if (abs(viewPosition.w) <= EPSILON) return vec3(uv * 2.0 - 1.0, 1.0);
    return mat3(InverseViewRotation) * (viewPosition.xyz / viewPosition.w);
}

vec3 reconstructDhRelative(vec2 uv, float depth) {
    vec4 clipPosition = vec4(uv, depth, 1.0);
    if (DepthInfo.w > 0.5) {
        clipPosition.xy = clipPosition.xy * 2.0 - 1.0;
    } else {
        clipPosition.xyz = clipPosition.xyz * 2.0 - 1.0;
    }
    vec4 relativePosition = DhInverseMvmProjection * clipPosition;
    if (abs(relativePosition.w) <= EPSILON) return vec3(uv * 2.0 - 1.0, 1.0);
    return relativePosition.xyz / relativePosition.w;
}

vec3 remapDhFogPosition(vec3 relativePosition) {
    float actualDistance = length(relativePosition);
    if (actualDistance <= EPSILON) return relativePosition;
    float fogDistance = clamp(
        actualDistance * DhDistanceInfo.x + DhDistanceInfo.y,
        0.0,
        DhDistanceInfo.z
    );
    return relativePosition * (fogDistance / actualDistance);
}

LayerSample sampleLayer(vec2 uv) {
    float mcDepth = texture(DepthSampler, uv).r;
    float dhDepth = texture(DhDepthSampler, uv).r;
    if (abs(mcDepth - DepthInfo.x) > EPSILON) return LayerSample(MC_LAYER, mcDepth, dhDepth);
    if (DepthInfo.y >= 0.0 && abs(dhDepth - DepthInfo.y) > EPSILON) {
        return LayerSample(DH_LAYER, mcDepth, dhDepth);
    }
    return LayerSample(FAR_LAYER, mcDepth, dhDepth);
}

bool farLayerCompatible(int leftLayer, int rightLayer) {
    return leftLayer == FAR_LAYER && rightLayer == FAR_LAYER;
}

float bilateralWeight(LayerSample center, LayerSample sampleValue, vec2 centerUv, vec2 sampleUv) {
    if (farLayerCompatible(center.layer, sampleValue.layer)) return 1.0;
    if (center.layer != sampleValue.layer) return 0.0;

    float centerDistance = center.layer == DH_LAYER
        ? length(reconstructDhRelative(centerUv, center.dhDepth))
        : length(reconstructRelative(centerUv, center.mcDepth));
    float sampleDistance = sampleValue.layer == DH_LAYER
        ? length(reconstructDhRelative(sampleUv, sampleValue.dhDepth))
        : length(reconstructRelative(sampleUv, sampleValue.mcDepth));
    float tolerance = max(1.0, centerDistance * 0.02);
    return clamp(1.0 - abs(sampleDistance - centerDistance) / tolerance, 0.0, 1.0);
}

float linearFogFactor(float distanceToCamera, float start, float end) {
    if (distanceToCamera <= start) return 0.0;
    if (distanceToCamera >= end) return 1.0;
    return (distanceToCamera - start) / (end - start);
}

float distanceFogFactor(vec3 relativePosition) {
    float sphericalDistance = length(relativePosition);
    float cylindricalDistance = max(length(relativePosition.xz), abs(relativePosition.y));
    return max(
        linearFogFactor(sphericalDistance, FogRanges.x, FogRanges.y),
        linearFogFactor(cylindricalDistance, FogRanges.z, FogRanges.w)
    );
}

float fogFactor(LayerSample center, vec2 uv) {
    if (center.layer == DH_LAYER) {
        return distanceFogFactor(remapDhFogPosition(reconstructDhRelative(uv, center.dhDepth)));
    }
    if (center.layer != MC_LAYER) return 1.0;
    return distanceFogFactor(reconstructRelative(uv, center.mcDepth));
}

void addPair(
    inout vec3 color,
    inout float totalWeight,
    LayerSample center,
    vec2 centerUv,
    vec4 pairData
) {
    if (pairData.y <= 0.0) return;
    vec2 offset = SampleInfo.xy * pairData.x;
    for (int side = -1; side <= 1; side += 2) {
        vec2 sampleUv = clamp(centerUv + offset * float(side), vec2(0.0), vec2(1.0));
        float weight = pairData.y * bilateralWeight(center, sampleLayer(sampleUv), centerUv, sampleUv);
        color += texture(BlurSampler, sampleUv).rgb * weight;
        totalWeight += weight;
    }
}

void main() {
    vec2 uv = clamp(texCoord, vec2(0.0), vec2(1.0));
    LayerSample center = sampleLayer(uv);
    vec3 color = texture(BlurSampler, uv).rgb * SampleInfo.z;
    float totalWeight = SampleInfo.z;
    addPair(color, totalWeight, center, uv, Pair0);
    addPair(color, totalWeight, center, uv, Pair1);
    addPair(color, totalWeight, center, uv, Pair2);
    addPair(color, totalWeight, center, uv, Pair3);
    addPair(color, totalWeight, center, uv, Pair4);
    addPair(color, totalWeight, center, uv, Pair5);
    float opacity = clamp(fogFactor(center, uv), 0.0, 1.0);
    if (opacity <= 0.001) discard;
    fragColor = vec4(color / max(totalWeight, EPSILON), opacity);
}
