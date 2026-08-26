#version 330 core

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D TerrainMaskSampler;
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

const int VOLUME_STEPS = 8;
const float EPSILON = 1.0e-6;
const float TERRAIN_THRESHOLD = 0.5;
const float BASE_DENSITY_FLOOR = 0.18;
const float SKY_FOG_FLOOR = 0.65;
const float CLEAR_DEPTH_INSET = 1.0e-5;
const float TWO_PI = 6.28318530718;
const int SOURCE_SKY = 0;
const int SOURCE_DH = 1;
const int SOURCE_VANILLA = 2;

bool isTerrain(vec2 uv) {
    return texture(TerrainMaskSampler, uv).r >= TERRAIN_THRESHOLD;
}

int terrainSource(vec2 uv) {
    return int(floor(texture(TerrainMaskSampler, uv).g * 2.0 + 0.5));
}

float depthToClip(float depth, float zeroToOne) {
    return zeroToOne > 0.5 ? depth : depth * 2.0 - 1.0;
}

vec3 reconstructVanillaClearSkyRelative(vec2 uv) {
    float clearDepth = clamp(VanillaDepthInfo.x, 0.0, 1.0);
    vec4 clearPosition = InverseProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(clearDepth, VanillaDepthInfo.y),
        1.0
    );
    if (abs(clearPosition.w) > EPSILON) return clearPosition.xyz / clearPosition.w;

    float finiteClearDepth = mix(clearDepth, 1.0 - clearDepth, CLEAR_DEPTH_INSET);
    vec4 finitePosition = InverseProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(finiteClearDepth, VanillaDepthInfo.y),
        1.0
    );
    vec3 finiteRelative = abs(finitePosition.w) > EPSILON
        ? finitePosition.xyz / finitePosition.w
        : vec3(uv * 2.0 - 1.0, 1.0);
    return normalize(finiteRelative) * max(HorizonInfo.y, 1.0);
}

vec3 reconstructDhClearSkyRelative(vec2 uv) {
    float clearDepth = clamp(DhDepthInfo.x, 0.0, 1.0);
    vec4 clearPosition = DhInverseMvmProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(clearDepth, DhDepthInfo.y),
        1.0
    );
    if (abs(clearPosition.w) > EPSILON) return clearPosition.xyz / clearPosition.w;

    float finiteClearDepth = mix(clearDepth, 1.0 - clearDepth, CLEAR_DEPTH_INSET);
    vec4 finitePosition = DhInverseMvmProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(finiteClearDepth, DhDepthInfo.y),
        1.0
    );
    if (abs(finitePosition.w) <= EPSILON) return vec3(0.0);
    vec3 finiteRelative = finitePosition.xyz / finitePosition.w;
    return normalize(finiteRelative) * max(HorizonInfo.y, 1.0);
}

vec3 reconstructVanillaSurfaceRelative(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    vec4 viewPosition = InverseProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(depth, VanillaDepthInfo.y),
        1.0
    );
    if (abs(viewPosition.w) <= EPSILON) return vec3(0.0);
    return mat3(InverseViewRotation) * (viewPosition.xyz / viewPosition.w);
}

vec3 reconstructDhSurfaceRelative(vec2 uv) {
    float depth = texture(DhDepthSampler, uv).r;
    vec4 relativePosition = DhInverseMvmProjection * vec4(
        uv * 2.0 - 1.0,
        depthToClip(depth, DhDepthInfo.y),
        1.0
    );
    if (abs(relativePosition.w) <= EPSILON) return vec3(0.0);
    return relativePosition.xyz / relativePosition.w;
}

bool isFiniteRelative(vec3 relativePosition) {
    float distanceToCamera = length(relativePosition);
    return distanceToCamera == distanceToCamera && distanceToCamera > EPSILON && distanceToCamera < 1.0e9;
}

vec3 reconstructClearSkyRelative(vec2 uv) {
    if (DhDepthInfo.z > 0.5) {
        vec3 dhRelative = reconstructDhClearSkyRelative(uv);
        if (isFiniteRelative(dhRelative)) return dhRelative;
    }

    vec3 vanillaRelative = reconstructVanillaClearSkyRelative(uv);
    return mat3(InverseViewRotation) * vanillaRelative;
}

vec3 validatedClearSkyRelative(vec3 relativePosition) {
    float reconstructedDistance = length(relativePosition);
    float configuredStart = max(min(HorizonInfo.x, HorizonInfo.y), 0.0);
    float configuredEnd = max(max(HorizonInfo.x, HorizonInfo.y), 1.0);
    float minimumPlausibleDistance = max(configuredStart * 0.5, 1.0);
    if (isFiniteRelative(relativePosition) && reconstructedDistance >= minimumPlausibleDistance) {
        return relativePosition;
    }

    vec3 direction = reconstructedDistance > EPSILON && reconstructedDistance == reconstructedDistance
        ? relativePosition / reconstructedDistance
        : vec3(0.0, 0.0, 1.0);
    return direction * configuredEnd;
}

float linearFogFactor(float distanceToCamera, float startDistance, float endDistance) {
    if (endDistance <= startDistance + EPSILON) {
        return step(endDistance, distanceToCamera);
    }
    return smoothstep(startDistance, endDistance, distanceToCamera);
}

float analyticBaseFog(vec3 worldRay, float clearSkyDistance, int source) {
    float startDistance = max(HorizonInfo.x, 0.0);
    float endDistance = max(HorizonInfo.y, startDistance);
    float horizonProximity = 1.0 - smoothstep(0.02, 0.72, abs(worldRay.y));
    float distanceFactor = linearFogFactor(clearSkyDistance, startDistance, endDistance);
    float density = mix(BASE_DENSITY_FLOOR, 1.75, clamp(HorizonInfo.z, 0.0, 1.0));
    float directionalFactor = source == SOURCE_SKY
        ? mix(SKY_FOG_FLOOR, 1.0, horizonProximity)
        : 1.0;
    return (1.0 - exp(-distanceFactor * density)) * directionalFactor;
}

float layerProfile(float normalizedHeight) {
    float groundWeight = 1.0 - smoothstep(0.0, 0.5, normalizedHeight);
    float upperWeight = smoothstep(0.5, 1.0, normalizedHeight);
    float middleWeight = max(0.0, 1.0 - groundWeight - upperWeight);
    return dot(vec3(groundWeight, middleWeight, upperWeight), LayerSettings.yzw);
}

float worldFogDensity(vec3 worldPosition) {
    float spacing = max(LayerSettings.x, 1.0);
    float drift = CameraPositionAndTime.w * 0.035;
    float horizontalWave = sin((worldPosition.x + worldPosition.z) * 0.018 + drift) * 0.12;
    float normalizedHeight = fract(worldPosition.y / spacing + horizontalWave);
    float broadVariation = 0.72 + 0.28 * sin(
        dot(worldPosition.xz, vec2(0.011, 0.014)) - drift * 0.7
    );
    return max(0.0, layerProfile(normalizedHeight) * broadVariation);
}

float cameraClearFactor(float distanceToCamera) {
    float clearRadius = max(VolumeSettings.z, 0.0);
    if (clearRadius <= EPSILON) return 1.0;
    return smoothstep(clearRadius, clearRadius + max(4.0, clearRadius * 0.5), distanceToCamera);
}

float multiLayerFogDensity(vec3 worldRay, float clearSkyDistance) {
    float startDistance = max(HorizonInfo.x, 0.0);
    float endDistance = min(max(HorizonInfo.y, startDistance + 1.0), clearSkyDistance);
    if (endDistance <= startDistance + EPSILON) return 0.0;
    float segmentLength = endDistance - startDistance;
    float accumulatedDensity = 0.0;

    for (int stepIndex = 0; stepIndex < VOLUME_STEPS; stepIndex++) {
        float progress = (float(stepIndex) + 0.5) / float(VOLUME_STEPS);
        float sampleDistance = startDistance + segmentLength * progress;
        vec3 worldPosition = CameraPositionAndTime.xyz + worldRay * sampleDistance;
        accumulatedDensity += worldFogDensity(worldPosition) * cameraClearFactor(sampleDistance);
    }

    float averageDensity = accumulatedDensity / float(VOLUME_STEPS);
    float volumeStrength = max(VolumeSettings.y, 0.0) * max(VolumeSettings.w, 0.0);
    return 1.0 - exp(-averageDensity * volumeStrength);
}

void main() {
    int source = terrainSource(texCoord);
    vec3 relativePosition = source == SOURCE_DH
        ? reconstructDhSurfaceRelative(texCoord)
        : (source == SOURCE_VANILLA
            ? reconstructVanillaSurfaceRelative(texCoord)
            : validatedClearSkyRelative(reconstructClearSkyRelative(texCoord)));
    if (!isFiniteRelative(relativePosition)) {
        relativePosition = validatedClearSkyRelative(reconstructClearSkyRelative(texCoord));
    }
    float surfaceDistance = length(relativePosition);
    vec3 worldRay = surfaceDistance > EPSILON
        ? relativePosition / surfaceDistance
        : vec3(0.0, 0.0, 1.0);
    float fogOpacity = analyticBaseFog(worldRay, surfaceDistance, source);
    if (VolumeSettings.x > 0.5) {
        float volumeOpacity = multiLayerFogDensity(worldRay, surfaceDistance);
        fogOpacity = 1.0 - (1.0 - fogOpacity) * (1.0 - volumeOpacity);
    }

    float finalAlpha = clamp(fogOpacity * FogColor.a, 0.0, 1.0);
    fragColor = vec4(FogColor.rgb * finalAlpha, finalAlpha);
}
