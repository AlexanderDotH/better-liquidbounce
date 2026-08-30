/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.render.engine.unifiedfog

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedFogShaderContractTest {

    @Test
    fun `terrain mask gives Vanilla priority and recognizes each source clear convention`() {
        val shader = read(MASK_SHADER)

        assertTrue(shader.contains("uniform sampler2D DepthSampler"))
        assertTrue(shader.contains("uniform sampler2D DhDepthSampler"))
        assertTrue(shader.contains("VanillaDepthInfo"))
        assertTrue(shader.contains("DhDepthInfo"))
        assertTrue(shader.contains("isDepthClear"))
        assertTrue(shader.contains("SOURCE_VANILLA"))
        assertTrue(shader.contains("SOURCE_DH"))
        assertTrue(shader.indexOf("if (vanillaTerrain)") < shader.indexOf("if (dhTerrain)"))
        assertTrue(shader.contains("terrainCoverage = source == SOURCE_SKY ? 0.0 : 1.0"))
        assertFalse(shader.contains("SceneSampler"))
    }

    @Test
    fun `fog field always generates analytic base fog and gates only optional multilayer density`() {
        val shader = read(GENERATE_SHADER)

        assertTrue(shader.contains("uniform sampler2D TerrainMaskSampler"))
        assertTrue(shader.contains("uniform sampler2D DepthSampler"))
        assertTrue(shader.contains("uniform sampler2D DhDepthSampler"))
        assertTrue(shader.contains("layout(std140) uniform UnifiedFogData"))
        assertTrue(shader.contains("linearFogFactor"))
        assertTrue(shader.contains("smoothstep(startDistance, endDistance, distanceToCamera)"))
        assertTrue(shader.contains("reconstructVanillaClearSkyRelative"))
        assertTrue(shader.contains("reconstructDhClearSkyRelative"))
        assertTrue(shader.contains("DhDepthInfo.z > 0.5"))
        assertTrue(shader.contains("clearSkyDistance"))
        assertTrue(shader.contains("validatedClearSkyRelative"))
        assertTrue(shader.contains("minimumPlausibleDistance"))
        assertTrue(shader.contains("depthToClip(clearDepth"))
        assertTrue(shader.contains("normalize(finiteRelative) * max(HorizonInfo.y"))
        assertTrue(shader.contains("analyticBaseFog"))
        assertTrue(shader.contains("SKY_FOG_FLOOR"))
        assertTrue(shader.contains("mix(SKY_FOG_FLOOR, 1.0, horizonProximity)"))
        assertTrue(shader.contains("multiLayerFogDensity"))
        assertTrue(shader.contains("terrainSource"))
        assertTrue(shader.contains("SOURCE_DH"))
        assertTrue(shader.contains("reconstructDhSurfaceRelative"))
        assertTrue(shader.contains("SOURCE_VANILLA"))
        assertTrue(shader.contains("reconstructVanillaSurfaceRelative"))
        assertTrue(shader.contains("VolumeSettings.x > 0.5"))
        assertTrue(shader.indexOf("analyticBaseFog") < shader.lastIndexOf("VolumeSettings.x > 0.5"))
        assertTrue(shader.contains("FogColor.a"))
        assertFalse(shader.contains("distanceToCamera = mix(startDistance, endDistance"))
        assertFalse(shader.contains("SceneSampler"))
    }

    @Test
    fun `fog blur reads only the generated fog field and renormalizes sky samples`() {
        val horizontal = read(BLUR_HORIZONTAL_SHADER)
        val vertical = read(BLUR_VERTICAL_SHADER)

        listOf(horizontal, vertical).forEach { shader ->
            assertTrue(shader.contains("uniform sampler2D FogSampler"))
            assertTrue(shader.contains("uniform sampler2D TerrainMaskSampler"))
            assertTrue(shader.contains("layout(std140) uniform UnifiedFogKernelData"))
            assertTrue(shader.contains("if (isTerrain(texCoord))"))
            assertTrue(shader.contains("fragColor = vec4(0.0)"))
            assertTrue(shader.contains("sampleSkyMask"))
            assertTrue(shader.contains("fogSum += sampledFog * sampleSkyMask * kernelWeight"))
            assertTrue(shader.contains("totalWeight += kernelWeight * sampleSkyMask"))
            assertFalse(shader.contains("SceneSampler"))
            assertFalse(shader.contains("DepthSampler"))
            assertFalse(shader.contains("DhDepthSampler"))
        }

        assertTrue(horizontal.contains("vec2(ViewportInfo.z, 0.0)"))
        assertTrue(vertical.contains("vec2(0.0, ViewportInfo.w)"))
    }

    @Test
    fun `composite preserves terrain fog and envelopes the sky side of chunk silhouettes`() {
        val shader = read(COMPOSITE_SHADER)

        assertTrue(shader.contains("uniform sampler2D FogSampler"))
        assertTrue(shader.contains("uniform sampler2D TerrainMaskSampler"))
        assertTrue(shader.contains("sourceLayer"))
        assertTrue(shader.contains("centerIsSky"))
        assertFalse(shader.contains("if (isTerrain(texCoord))"))
        assertTrue(shader.contains("skyEnvelopeFactor"))
        assertTrue(shader.contains("SKY_ENVELOPE_BOOST"))
        assertTrue(shader.contains("if (HorizonInfo.w <= EPSILON) return 1.0"))
        assertTrue(shader.contains("clamp(HorizonInfo.w, 0.0, MAX_FEATHER_PIXELS)"))
        assertTrue(shader.contains("ViewportInfo.zw"))
        assertTrue(shader.contains("DIAGONAL = 0.70710678118"))
        assertTrue(shader.contains("diagonalOffset"))
        assertTrue(shader.contains("vec4 envelopingFog = fog * skyEnvelope"))
        assertFalse(shader.contains("SceneSampler"))
        assertFalse(shader.contains("DepthSampler"))
        assertFalse(shader.contains("DhDepthSampler"))
    }

    @Test
    fun `all stages share the fixed unified fog frame layout`() {
        val shaders = ALL_SHADERS.map(::read)
        val requiredFields = listOf(
            "mat4 InverseProjection",
            "mat4 InverseViewRotation",
            "mat4 DhInverseMvmProjection",
            "vec4 FogColor",
            "vec4 HorizonInfo",
            "vec4 CameraPositionAndTime",
            "vec4 VanillaDepthInfo",
            "vec4 DhDepthInfo",
            "vec4 ViewportInfo",
            "vec4 VolumeSettings",
            "vec4 LayerSettings",
        )

        shaders.forEach { shader ->
            assertTrue(shader.startsWith("#version 330 core"))
            assertTrue(shader.contains("layout(std140) uniform UnifiedFogData"))
            requiredFields.forEach { field -> assertTrue(shader.contains(field), "Missing $field") }
        }
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val SHADER_ROOT = "src/main/resources/resources/liquidbounce/shaders/fog"
        const val MASK_SHADER = "$SHADER_ROOT/unified_mask.frag"
        const val GENERATE_SHADER = "$SHADER_ROOT/unified_generate.frag"
        const val BLUR_HORIZONTAL_SHADER = "$SHADER_ROOT/unified_blur_horizontal.frag"
        const val BLUR_VERTICAL_SHADER = "$SHADER_ROOT/unified_blur_vertical.frag"
        const val COMPOSITE_SHADER = "$SHADER_ROOT/unified_composite.frag"
        val ALL_SHADERS = listOf(
            MASK_SHADER,
            GENERATE_SHADER,
            BLUR_HORIZONTAL_SHADER,
            BLUR_VERTICAL_SHADER,
            COMPOSITE_SHADER,
        )
    }
}
