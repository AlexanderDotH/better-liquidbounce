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

internal fun shouldReplaceNativeFog(
    unifiedEnabled: Boolean,
    distantHorizonsInstalled: Boolean,
    compatibleDistantHorizonsDepthAvailable: Boolean,
): Boolean = unifiedEnabled && (!distantHorizonsInstalled || compatibleDistantHorizonsDepthAvailable)

internal data class UnifiedFogFrameRequest<T : Any>(
    val expectedFrameToken: TerrainFrameToken,
    val targetDimensions: FrameDimensions,
    val vanillaSource: TerrainDepthSource<T>,
    val distantHorizonsSource: OptionalTerrainDepthSource<T>,
    val horizonRange: PhysicalFogHorizonRange,
    val distantHorizonsValidationPolicy: TerrainDepthValidationPolicy = TerrainDepthValidationPolicy.STRICT,
)

internal data class UnifiedFogFrame<T : Any>(
    val frameToken: TerrainFrameToken,
    val dimensions: FrameDimensions,
    val vanillaSource: TerrainDepthSource<T>,
    val distantHorizonsSource: TerrainDepthSource<T>?,
    val horizonRange: PhysicalFogHorizonRange,
)

internal sealed interface UnifiedFogFrameBuild<out T : Any> {
    data class Ready<T : Any>(val frame: UnifiedFogFrame<T>) : UnifiedFogFrameBuild<T>

    data class Skipped(val rejection: TerrainDepthSourceRejection) : UnifiedFogFrameBuild<Nothing>
}

internal object UnifiedFogFrameFactory {

    fun <T : Any> build(request: UnifiedFogFrameRequest<T>): UnifiedFogFrameBuild<T> {
        val vanillaRejection = validateVanilla(request)
        if (vanillaRejection != null) return UnifiedFogFrameBuild.Skipped(vanillaRejection)

        return when (val distantHorizons = request.distantHorizonsSource) {
            OptionalTerrainDepthSource.Absent -> ready(request, null)
            is OptionalTerrainDepthSource.Ready -> readyWithDistantHorizons(request, distantHorizons.source)
            is OptionalTerrainDepthSource.Unavailable -> unavailableDistantHorizons(distantHorizons.reason)
        }
    }

    private fun validateVanilla(request: UnifiedFogFrameRequest<*>): TerrainDepthSourceRejection? =
        TerrainDepthSourceValidator.validate(
            source = request.vanillaSource,
            expectedKind = TerrainDepthKind.VANILLA,
            expectedFrameToken = request.expectedFrameToken,
            expectedDimensions = request.targetDimensions,
        )

    private fun <T : Any> readyWithDistantHorizons(
        request: UnifiedFogFrameRequest<T>,
        source: TerrainDepthSource<T>,
    ): UnifiedFogFrameBuild<T> {
        val rejection = TerrainDepthSourceValidator.validate(
            source = source,
            expectedKind = TerrainDepthKind.DISTANT_HORIZONS,
            expectedFrameToken = request.expectedFrameToken,
            expectedDimensions = request.targetDimensions,
            policy = request.distantHorizonsValidationPolicy,
        )
        if (rejection != null) return UnifiedFogFrameBuild.Skipped(rejection)
        return ready(request, source)
    }

    private fun unavailableDistantHorizons(
        reason: TerrainDepthUnavailableReason,
    ): UnifiedFogFrameBuild.Skipped = UnifiedFogFrameBuild.Skipped(
        TerrainDepthSourceRejection(
            sourceKind = TerrainDepthKind.DISTANT_HORIZONS,
            reason = TerrainDepthSourceRejectionReason.UNAVAILABLE,
            unavailableReason = reason,
        ),
    )

    private fun <T : Any> ready(
        request: UnifiedFogFrameRequest<T>,
        distantHorizonsSource: TerrainDepthSource<T>?,
    ): UnifiedFogFrameBuild.Ready<T> = UnifiedFogFrameBuild.Ready(
        UnifiedFogFrame(
            frameToken = request.expectedFrameToken,
            dimensions = request.targetDimensions,
            vanillaSource = request.vanillaSource,
            distantHorizonsSource = distantHorizonsSource,
            horizonRange = request.horizonRange,
        ),
    )
}
