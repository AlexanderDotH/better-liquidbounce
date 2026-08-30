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

import com.mojang.blaze3d.textures.GpuTextureView
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthStatus
import net.ccbluex.liquidbounce.render.engine.unifiedfog.UnifiedFogFrame

internal object FogFrameDiagnostics {

    fun recordRendered(
        input: FogFrameInput,
        frame: UnifiedFogFrame<GpuTextureView>,
        passCount: Int,
    ) {
        UnifiedFogDebug.record(
            state(
                status = input.status,
                vanillaReady = true,
                distantHorizonsReady = frame.distantHorizonsSource != null,
                horizonStart = input.horizon.startBlocks,
                horizonEnd = input.horizon.endBlocks,
                passCount = passCount,
                skipReason = null,
            )
        )
    }

    fun recordSkipped(
        reason: String,
        status: DistantHorizonsDepthStatus? = null,
        horizonStart: Float = 0f,
        horizonEnd: Float = 0f,
        vanillaReady: Boolean = false,
    ) {
        UnifiedFogDebug.record(state(status, vanillaReady, false, horizonStart, horizonEnd, 0, reason))
    }

    private fun state(
        status: DistantHorizonsDepthStatus?,
        vanillaReady: Boolean,
        distantHorizonsReady: Boolean,
        horizonStart: Float,
        horizonEnd: Float,
        passCount: Int,
        skipReason: String?,
    ) = UnifiedFogDebugState(
        engine = "Unified",
        vanillaReady = vanillaReady,
        distantHorizonsReady = distantHorizonsReady,
        distantHorizonsBackend = status?.backend,
        distantHorizonsApiVersion = status?.apiVersion,
        frameAge = status?.frameAge ?: 0L,
        horizonStartBlocks = horizonStart,
        horizonEndBlocks = horizonEnd,
        passCount = passCount,
        skipReason = skipReason,
    )
}
