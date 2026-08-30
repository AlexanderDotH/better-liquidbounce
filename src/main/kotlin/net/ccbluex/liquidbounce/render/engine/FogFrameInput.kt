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
package net.ccbluex.liquidbounce.render.engine

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthStatus
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthTexture
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsSourceReadiness
import net.ccbluex.liquidbounce.render.engine.unifiedfog.ClipDepthRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.FrameDimensions
import net.ccbluex.liquidbounce.render.engine.unifiedfog.InverseReconstructionMatrix
import net.ccbluex.liquidbounce.render.engine.unifiedfog.OptionalTerrainDepthSource
import net.ccbluex.liquidbounce.render.engine.unifiedfog.PhysicalFogHorizonRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainClipRange
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthConvention
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthKind
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthSource
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthUnavailableReason
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainDepthValidationPolicy
import net.ccbluex.liquidbounce.render.engine.unifiedfog.TerrainFrameToken
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrameRequest
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import org.joml.Matrix4f
import org.joml.Matrix4fc

internal const val FOG_MAX_DH_FRAME_AGE = 1L

internal data class FogFrameInput(
    val request: UnifiedFogFrameRequest<GpuTextureView>,
    val status: DistantHorizonsDepthStatus,
    val horizon: PhysicalFogHorizonRange,
)

internal object FogFrameInputFactory {

    fun build(
        target: RenderTarget,
        token: TerrainFrameToken,
        projectionMatrix: Matrix4fc,
        status: DistantHorizonsDepthStatus,
        distantHorizonsDepth: DistantHorizonsDepthTexture?,
        vanillaRenderDistanceChunks: Int,
    ): FogFrameInput? {
        val dimensions = FrameDimensions(target.width, target.height)
        val vanillaDepth = target.depthTextureView ?: return null
        val horizon = CustomFogRenderBridge.currentUnifiedHorizon(
            distantHorizonsDepth?.farClipPlane,
            vanillaRenderDistanceChunks,
        )
        val physicalHorizon = PhysicalFogHorizonRange(horizon.startBlocks, horizon.endBlocks)
        return FogFrameInput(
            request = UnifiedFogFrameRequest(
                expectedFrameToken = token,
                targetDimensions = dimensions,
                vanillaSource = vanillaSource(
                    vanillaDepth,
                    dimensions,
                    token,
                    projectionMatrix,
                    horizon.visibleDistanceBlocks,
                ),
                distantHorizonsSource = distantHorizonsSource(status, distantHorizonsDepth, token),
                horizonRange = physicalHorizon,
                distantHorizonsValidationPolicy = TerrainDepthValidationPolicy.renderCompatible(
                    FOG_MAX_DH_FRAME_AGE
                ),
            ),
            status = status,
            horizon = physicalHorizon,
        )
    }

    private fun vanillaSource(
        depth: GpuTextureView,
        dimensions: FrameDimensions,
        token: TerrainFrameToken,
        projectionMatrix: Matrix4fc,
        visibleDistanceBlocks: Float,
    ) = TerrainDepthSource(
        kind = TerrainDepthKind.VANILLA,
        textureView = depth,
        depthConvention = TerrainDepthConvention(MC_CLEAR_DEPTH, gpuClipDepthRange()),
        inverseReconstruction = InverseReconstructionMatrix(Matrix4f(projectionMatrix).invert()),
        clipRange = TerrainClipRange(0f, visibleDistanceBlocks.coerceAtLeast(MIN_CLIP_DISTANCE)),
        dimensions = dimensions,
        frameToken = token,
    )

    private fun distantHorizonsSource(
        status: DistantHorizonsDepthStatus,
        depth: DistantHorizonsDepthTexture?,
        token: TerrainFrameToken,
    ): OptionalTerrainDepthSource<GpuTextureView> {
        if (status.readiness == DistantHorizonsSourceReadiness.ABSENT) {
            return OptionalTerrainDepthSource.Absent
        }
        if (depth == null) return OptionalTerrainDepthSource.Unavailable(status.toUnavailableReason())

        val clipRange = runCatching { TerrainClipRange(depth.nearClipPlane, depth.farClipPlane) }
            .getOrElse { return OptionalTerrainDepthSource.Unavailable(TerrainDepthUnavailableReason.DEPTH_NOT_READY) }
        return OptionalTerrainDepthSource.Ready(
            TerrainDepthSource(
                kind = TerrainDepthKind.DISTANT_HORIZONS,
                textureView = depth.textureView,
                depthConvention = TerrainDepthConvention(
                    depth.clearDepth,
                    if (depth.zeroToOneDepth) ClipDepthRange.ZERO_TO_ONE else ClipDepthRange.NEGATIVE_ONE_TO_ONE,
                ),
                inverseReconstruction = InverseReconstructionMatrix(depth.inverseMvmProjection),
                clipRange = clipRange,
                dimensions = FrameDimensions(depth.width, depth.height),
                frameToken = TerrainFrameToken(token.lifecycleGeneration, depth.frameToken),
            )
        )
    }

    private fun gpuClipDepthRange() =
        if (gpuDevice.deviceInfo.isZZeroToOne) ClipDepthRange.ZERO_TO_ONE else ClipDepthRange.NEGATIVE_ONE_TO_ONE

    private fun DistantHorizonsDepthStatus.toUnavailableReason() = when (readiness) {
        DistantHorizonsSourceReadiness.UNSUPPORTED -> TerrainDepthUnavailableReason.UNSUPPORTED_CAPABILITY
        else -> TerrainDepthUnavailableReason.DEPTH_NOT_READY
    }

    private const val MC_CLEAR_DEPTH = 0f
    private const val MIN_CLIP_DISTANCE = 1f
}
