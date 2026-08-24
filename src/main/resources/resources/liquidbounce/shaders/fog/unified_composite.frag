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

bool isTerrain(vec2 uv) {
    return texture(TerrainMaskSampler, uv).r >= TERRAIN_THRESHOLD;
}

float terrainSample(vec2 uv) {
    vec2 boundedUv = clamp(uv, vec2(0.0), vec2(1.0));
    return step(TERRAIN_THRESHOLD, texture(TerrainMaskSampler, boundedUv).r);
}

float skySideFeatherFactor(vec2 uv) {
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
            nearestTerrainDistance = min(nearestTerrainDistance, normalizedDistance);
        }
    }
    return smoothstep(0.0, 1.0, nearestTerrainDistance);
}

void main() {
    if (isTerrain(texCoord)) {
        fragColor = vec4(0.0);
        return;
    }

    vec4 fog = texture(FogSampler, texCoord);
    float skyFeather = skySideFeatherFactor(texCoord);
    vec4 featheredFog = fog * skyFeather;
    float fogAlpha = clamp(featheredFog.a, 0.0, 1.0);
    if (fogAlpha <= EPSILON) {
        fragColor = vec4(0.0);
        return;
    }

    // The fog field is stored premultiplied so filtering cannot create color
    // fringes. The translucent pipeline expects straight-alpha output.
    fragColor = vec4(featheredFog.rgb / fogAlpha, fogAlpha);
}
