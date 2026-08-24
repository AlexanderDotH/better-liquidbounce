#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D DepthSampler;
uniform sampler2D DhDepthSampler;

layout(std140) uniform FogVolumeData {
    mat4 InverseProjection;
    mat4 InverseViewRotation;
    mat4 DhInverseMvmProjection;
    vec4 FogColor;
    vec4 FogRanges;
    vec4 CameraPositionAndTime;
    vec4 VolumeSettings;
    vec4 DepthInfo;
    vec4 LayerSettings;
    vec4 DhDistanceInfo;
};

const int VOLUME_STEPS = 8;
const float EPSILON = 1.0e-6;
const float TWO_PI = 6.28318530718;

float depthToClip(float depth) {
    return VolumeSettings.w > 0.5 ? depth : depth * 2.0 - 1.0;
}

vec3 reconstructRelative(vec2 uv, float depth) {
    vec4 viewPosition = InverseProjection * vec4(uv * 2.0 - 1.0, depthToClip(depth), 1.0);
    if (abs(viewPosition.w) <= EPSILON) {
        return vec3(uv * 2.0 - 1.0, 1.0);
    }
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

float worldFogDensity(vec3 worldPosition) {
    vec3 p = worldPosition * (TWO_PI / max(VolumeSettings.y, 1.0));
    float time = CameraPositionAndTime.w;
    p += vec3(time * 0.18, time * 0.04, -time * 0.12);

    // Integer harmonics make the density periodic at the wrapped world boundary.
    float broad = sin(p.x * 18.0 + p.z * 5.0);
    float vertical = sin(p.y * 24.0 - p.x * 4.0);
    float detail = sin(dot(p, vec3(10.0, 7.0, 16.0)));
    return clamp(0.5 + broad * 0.24 + vertical * 0.16 + detail * 0.10, 0.0, 1.0);
}

float multiLayerFogDensity(vec3 relativePosition, vec3 worldPosition) {
    float baseDensity = worldFogDensity(worldPosition);
    float spacing = LayerSettings.x;
    if (spacing <= 0.0) return baseDensity;

    float height = relativePosition.y / spacing;
    float ground = exp(-pow((height + 0.35) / 0.65, 2.0)) * LayerSettings.y;
    float middle = exp(-pow((height - 0.45) / 0.85, 2.0)) * LayerSettings.z;
    float upper = exp(-pow((height - 1.60) / 1.10, 2.0)) * LayerSettings.w;
    float strata = 0.85 + 0.15 * sin(worldPosition.y / spacing * TWO_PI + CameraPositionAndTime.w * 0.35);
    float layerEnvelope = 0.30 + min(ground + middle + upper, 1.25) * 0.75;
    return clamp(baseDensity * layerEnvelope * strata, 0.0, 1.0);
}

float cameraClearFactor(float distanceToCamera) {
    float radius = DepthInfo.y;
    if (radius <= 0.0) return 1.0;
    float transition = max(4.0, radius * 0.75);
    return smoothstep(radius, radius + transition, distanceToCamera);
}

float screenDither() {
    return fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = clamp(texCoord, vec2(0.0), vec2(1.0));
    float mcDepth = texture(DepthSampler, uv).r;
    float dhDepth = texture(DhDepthSampler, uv).r;
    bool mcDrawn = mcDepth != DepthInfo.x;
    bool dhDrawn = DepthInfo.z >= 0.0 && dhDepth != DepthInfo.z;

    vec3 mcRelative = reconstructRelative(uv, mcDrawn ? mcDepth : DepthInfo.x);
    vec3 dhRelative = reconstructDhRelative(uv, dhDepth);
    vec3 rayPoint = mcDrawn ? mcRelative : (dhDrawn ? dhRelative : mcRelative);
    vec3 rayDirection = normalize(rayPoint);
    float dhSurfaceDistance = dhDrawn ? length(dhRelative) : VolumeSettings.z;
    float surfaceDistance = mcDrawn ? min(length(mcRelative), VolumeSettings.z) : dhSurfaceDistance;
    if (surfaceDistance <= EPSILON) discard;

    float opticalDepth = 0.0;
    float jitter = screenDither();
    for (int stepIndex = 0; stepIndex < VOLUME_STEPS; stepIndex++) {
        float progress = (float(stepIndex) + 0.35 + jitter * 0.3) / float(VOLUME_STEPS);
        vec3 relativePosition = rayDirection * (surfaceDistance * progress);
        vec3 worldPosition = CameraPositionAndTime.xyz + relativePosition;
        float density = 0.2 + multiLayerFogDensity(relativePosition, worldPosition) * 0.8;
        float distanceToCamera = length(relativePosition);
        vec3 fogPosition = (!mcDrawn && dhDrawn) ? remapDhFogPosition(relativePosition) : relativePosition;
        opticalDepth += density * distanceFogFactor(fogPosition) * cameraClearFactor(distanceToCamera);
    }

    opticalDepth *= VolumeSettings.x / float(VOLUME_STEPS);
    float volumeAlpha = (1.0 - exp(-opticalDepth * 1.8)) * FogColor.a;
    if (volumeAlpha <= 0.001) discard;
    fragColor = vec4(FogColor.rgb, clamp(volumeAlpha, 0.0, 1.0));
}
