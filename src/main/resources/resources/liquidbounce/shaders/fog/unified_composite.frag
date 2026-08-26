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

const float EPSILON = 1.0e-6;
const float TERRAIN_THRESHOLD = 0.5;
const float MAX_FEATHER_PIXELS = 32.0;
const float DIAGONAL = 0.70710678118;
const int FEATHER_RING_COUNT = 4;
const int SOURCE_SKY = 0;
const float SKY_ENVELOPE_BOOST = 0.35;

int sourceLayer(vec2 uv) {
    return int(floor(texture(TerrainMaskSampler, uv).g * 2.0 + 0.5));
}

float terrainSample(vec2 uv) {
    vec2 boundedUv = clamp(uv, vec2(0.0), vec2(1.0));
    return step(TERRAIN_THRESHOLD, texture(TerrainMaskSampler, boundedUv).r);
}

float skyEnvelopeFactor(vec2 uv) {
    if (HorizonInfo.w <= EPSILON) return 1.0;

    float featherPixels = clamp(HorizonInfo.w, 0.0, MAX_FEATHER_PIXELS);
    float nearestTerrainDistance = 1.0;
    for (int ringIndex = 1; ringIndex <= FEATHER_RING_COUNT; ringIndex++) {
        float normalizedDistance = float(ringIndex) / float(FEATHER_RING_COUNT);
        vec2 offset = ViewportInfo.zw * featherPixels * normalizedDistance;
        vec2 diagonalOffset = offset * DIAGONAL;
        float cardinalTerrain = max(
            max(terrainSample(uv + vec2(offset.x, 0.0)), terrainSample(uv - vec2(offset.x, 0.0))),
            max(terrainSample(uv + vec2(0.0, offset.y)), terrainSample(uv - vec2(0.0, offset.y)))
        );
        float diagonalTerrain = max(
            max(terrainSample(uv + diagonalOffset), terrainSample(uv - diagonalOffset)),
            max(
                terrainSample(uv + vec2(diagonalOffset.x, -diagonalOffset.y)),
                terrainSample(uv + vec2(-diagonalOffset.x, diagonalOffset.y))
            )
        );
        float ringTerrain = max(cardinalTerrain, diagonalTerrain);
        if (ringTerrain > 0.5) {
            float estimatedEdgeDistance = max(
                normalizedDistance - 1.0 / float(FEATHER_RING_COUNT),
                0.0
            );
            nearestTerrainDistance = min(nearestTerrainDistance, estimatedEdgeDistance);
        }
    }
    float terrainProximity = 1.0 - smoothstep(0.0, 1.0, nearestTerrainDistance);
    return 1.0 + SKY_ENVELOPE_BOOST * terrainProximity;
}

void main() {
    vec4 fog = texture(FogSampler, texCoord);
    bool centerIsSky = sourceLayer(texCoord) == SOURCE_SKY;
    float skyEnvelope = centerIsSky ? skyEnvelopeFactor(texCoord) : 1.0;
    vec4 envelopingFog = fog * skyEnvelope;
    float fogAlpha = clamp(envelopingFog.a, 0.0, 1.0);
    if (fogAlpha <= EPSILON) {
        fragColor = vec4(0.0);
        return;
    }

    // The fog field is stored premultiplied so filtering cannot create color
    // fringes. The translucent pipeline expects straight-alpha output.
    fragColor = vec4(envelopingFog.rgb / max(envelopingFog.a, EPSILON), fogAlpha);
}
