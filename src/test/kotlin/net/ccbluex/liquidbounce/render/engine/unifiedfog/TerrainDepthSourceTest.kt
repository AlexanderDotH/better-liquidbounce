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

import org.joml.Matrix4f
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TerrainDepthSourceTest {

    @Test
    fun `current source with matching dimensions is accepted`() {
        val expectedToken = TerrainFrameToken(lifecycleGeneration = 4, frameIndex = 81)
        val dimensions = FrameDimensions(width = 1920, height = 1080)
        val source = depthSource(frameToken = expectedToken, dimensions = dimensions)

        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = expectedToken,
            expectedDimensions = dimensions,
        )

        assertNull(rejection)
    }

    @Test
    fun `source from an earlier frame is rejected as stale`() {
        val source = depthSource(frameToken = TerrainFrameToken(2, 40))

        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = TerrainFrameToken(2, 41),
            expectedDimensions = source.dimensions,
        )

        assertEquals(
            TerrainDepthSourceRejection(
                sourceKind = TerrainDepthKind.VANILLA,
                reason = TerrainDepthSourceRejectionReason.STALE_FRAME,
            ),
            rejection,
        )
    }

    @Test
    fun `source from an earlier lifecycle is rejected as stale`() {
        val source = depthSource(frameToken = TerrainFrameToken(6, 12))

        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = TerrainFrameToken(7, 12),
            expectedDimensions = source.dimensions,
        )

        assertEquals(TerrainDepthSourceRejectionReason.STALE_FRAME, rejection?.reason)
    }

    @Test
    fun `source with pre-resize dimensions is rejected`() {
        val source = depthSource(dimensions = FrameDimensions(1280, 720))

        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = source.frameToken,
            expectedDimensions = FrameDimensions(1920, 1080),
        )

        assertEquals(TerrainDepthSourceRejectionReason.WRONG_SIZE, rejection?.reason)
    }

    @Test
    fun `vanilla slot rejects a DH source`() {
        val source = depthSource(kind = TerrainDepthKind.DISTANT_HORIZONS)

        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = source.frameToken,
            expectedDimensions = source.dimensions,
        )

        assertEquals(TerrainDepthSourceRejectionReason.WRONG_SOURCE_KIND, rejection?.reason)
    }

    @Test
    fun `depth metadata rejects invalid dimensions clip ranges and clear depth`() {
        assertFailsWith<IllegalArgumentException> { FrameDimensions(0, 1080) }
        assertFailsWith<IllegalArgumentException> { TerrainClipRange(nearPlane = 64f, farPlane = 64f) }
        assertFailsWith<IllegalArgumentException> {
            TerrainDepthConvention(clearDepth = 2f, clipDepthRange = ClipDepthRange.ZERO_TO_ONE)
        }
    }

    private fun depthSource(
        kind: TerrainDepthKind = TerrainDepthKind.VANILLA,
        frameToken: TerrainFrameToken = TerrainFrameToken(1, 20),
        dimensions: FrameDimensions = FrameDimensions(1920, 1080),
    ) = TerrainDepthSource(
        kind = kind,
        textureView = "depth-texture",
        depthConvention = TerrainDepthConvention(0f, ClipDepthRange.ZERO_TO_ONE),
        inverseReconstruction = InverseReconstructionMatrix(Matrix4f()),
        clipRange = TerrainClipRange(nearPlane = 0.05f, farPlane = 1024f),
        dimensions = dimensions,
        frameToken = frameToken,
    )
}
