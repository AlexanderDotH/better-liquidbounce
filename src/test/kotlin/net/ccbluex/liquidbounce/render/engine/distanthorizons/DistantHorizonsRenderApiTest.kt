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
package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DistantHorizonsRenderApiTest {

    @Test
    fun `DH API matrix values preserve their published column major order`() {
        val values = FloatArray(16) { index -> index + 0.25f }
        val copied = FloatArray(16)

        matrixFromDhValues(values).get(copied)

        assertContentEquals(values, copied)
    }

    @Test
    fun `DH API matrix rejects incomplete values`() {
        assertFailsWith<IllegalArgumentException> {
            matrixFromDhValues(FloatArray(15))
        }
    }

    @Test
    fun `DH clip range spreads fog across the complete LOD distance`() {
        val mapping = DistantHorizonsFogDistanceMapping.from(
            nearClip = 512f,
            farClip = 8192f,
            fogMaxDistance = 512f,
        )

        assertEquals(0f, mapping.mapDistance(512f), 0.001f)
        assertEquals(256f, mapping.mapDistance(4352f), 0.001f)
        assertEquals(512f, mapping.mapDistance(8192f), 0.001f)
        assertEquals(512f, mapping.mapDistance(16000f), 0.001f)
    }

    @Test
    fun `DH mapping targets the earliest complete fog boundary`() {
        assertEquals(517f, distantHorizonsFogSaturationDistance(1024f, 517f))
        assertEquals(256f, distantHorizonsFogSaturationDistance(256f, 512f))
        assertEquals(16f, distantHorizonsFogSaturationDistance(-20f, 0f))
    }

    @Test
    fun `invalid DH clip range falls back to identity distance`() {
        val mapping = DistantHorizonsFogDistanceMapping.from(100f, 100f, 512f)

        assertEquals(80f, mapping.mapDistance(80f), 0.001f)
        assertEquals(512f, mapping.mapDistance(900f), 0.001f)
    }

    @Test
    fun `typed DH render state is valid only for its captured frame`() {
        val renderParam = DistantHorizonsPublicRenderParam(
            inverseMvmProjection = FloatArray(16).also { values ->
                values[0] = 1f
                values[5] = 1f
                values[10] = 1f
                values[15] = 1f
            },
            nearClipPlane = 256f,
            farClipPlane = 8192f,
        )

        DistantHorizonsRenderApi.captureRenderParam(renderParam, frameToken = 71L)

        assertEquals(71L, DistantHorizonsRenderApi.state(71L)?.frameToken)
        assertEquals(8192f, DistantHorizonsRenderApi.state(71L)?.farClipPlane)
        assertNull(DistantHorizonsRenderApi.state(72L))
        DistantHorizonsRenderApi.invalidate()
        assertNull(DistantHorizonsRenderApi.state())
    }
}
