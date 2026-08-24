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
import kotlin.test.assertIs
import kotlin.test.assertNull

class UnifiedFogFrameTest {

    @Test
    fun `accepted frame carries the already resolved physical horizon`() {
        val resolvedHorizon = PhysicalFogHorizonRange(startBlocks = 7_000f, endBlocks = 10_000f)
        val request = frameRequest(
            distantHorizons = OptionalTerrainDepthSource.Ready(
                depthSource(TerrainDepthKind.DISTANT_HORIZONS, farPlane = 10_000f),
            ),
            horizonRange = resolvedHorizon,
        )

        val frame = assertIs<UnifiedFogFrameBuild.Ready<String>>(UnifiedFogFrameFactory.build(request)).frame

        assertEquals(resolvedHorizon, frame.horizonRange)
    }

    @Test
    fun `DH absence keeps a valid Vanilla-only frame`() {
        val request = frameRequest(distantHorizons = OptionalTerrainDepthSource.Absent)

        val frame = assertIs<UnifiedFogFrameBuild.Ready<String>>(UnifiedFogFrameFactory.build(request)).frame

        assertNull(frame.distantHorizonsSource)
        assertEquals(request.horizonRange, frame.horizonRange)
    }

    @Test
    fun `stale DH source skips the complete fog frame`() {
        val staleDh = depthSource(
            kind = TerrainDepthKind.DISTANT_HORIZONS,
            frameToken = TerrainFrameToken(3, 98),
        )
        val request = frameRequest(
            expectedToken = TerrainFrameToken(3, 99),
            vanilla = depthSource(frameToken = TerrainFrameToken(3, 99)),
            distantHorizons = OptionalTerrainDepthSource.Ready(staleDh),
        )

        val skipped = assertIs<UnifiedFogFrameBuild.Skipped>(UnifiedFogFrameFactory.build(request))

        assertEquals(TerrainDepthKind.DISTANT_HORIZONS, skipped.rejection.sourceKind)
        assertEquals(TerrainDepthSourceRejectionReason.STALE_FRAME, skipped.rejection.reason)
    }

    @Test
    fun `wrong-sized DH source skips the complete fog frame`() {
        val dh = depthSource(
            kind = TerrainDepthKind.DISTANT_HORIZONS,
            dimensions = FrameDimensions(960, 540),
        )
        val request = frameRequest(distantHorizons = OptionalTerrainDepthSource.Ready(dh))

        val skipped = assertIs<UnifiedFogFrameBuild.Skipped>(UnifiedFogFrameFactory.build(request))

        assertEquals(TerrainDepthSourceRejectionReason.WRONG_SIZE, skipped.rejection.reason)
    }

    @Test
    fun `installed DH without current depth skips instead of covering unknown LOD terrain`() {
        val request = frameRequest(
            distantHorizons = OptionalTerrainDepthSource.Unavailable(
                TerrainDepthUnavailableReason.DEPTH_NOT_READY,
            ),
        )

        val skipped = assertIs<UnifiedFogFrameBuild.Skipped>(UnifiedFogFrameFactory.build(request))

        assertEquals(TerrainDepthKind.DISTANT_HORIZONS, skipped.rejection.sourceKind)
        assertEquals(TerrainDepthSourceRejectionReason.UNAVAILABLE, skipped.rejection.reason)
        assertEquals(TerrainDepthUnavailableReason.DEPTH_NOT_READY, skipped.rejection.unavailableReason)
    }

    private fun frameRequest(
        expectedToken: TerrainFrameToken = TerrainFrameToken(3, 99),
        vanilla: TerrainDepthSource<String> = depthSource(frameToken = expectedToken),
        distantHorizons: OptionalTerrainDepthSource<String> = OptionalTerrainDepthSource.Absent,
        horizonRange: PhysicalFogHorizonRange = PhysicalFogHorizonRange(268.8f, 384f),
    ) = UnifiedFogFrameRequest(
        expectedFrameToken = expectedToken,
        targetDimensions = FrameDimensions(1920, 1080),
        vanillaSource = vanilla,
        distantHorizonsSource = distantHorizons,
        horizonRange = horizonRange,
    )

    private fun depthSource(
        kind: TerrainDepthKind = TerrainDepthKind.VANILLA,
        farPlane: Float = 2048f,
        frameToken: TerrainFrameToken = TerrainFrameToken(3, 99),
        dimensions: FrameDimensions = FrameDimensions(1920, 1080),
    ) = TerrainDepthSource(
        kind = kind,
        textureView = if (kind == TerrainDepthKind.VANILLA) "vanilla-depth" else "dh-depth",
        depthConvention = TerrainDepthConvention(0f, ClipDepthRange.ZERO_TO_ONE),
        inverseReconstruction = InverseReconstructionMatrix(Matrix4f()),
        clipRange = TerrainClipRange(nearPlane = 0.05f, farPlane = farPlane),
        dimensions = dimensions,
        frameToken = frameToken,
    )
}
