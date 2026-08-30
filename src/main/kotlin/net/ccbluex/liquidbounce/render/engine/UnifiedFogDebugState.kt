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

import net.ccbluex.liquidbounce.render.RENDER_CLIENT_NAME
import org.apache.logging.log4j.LogManager

data class UnifiedFogDebugState(
    val engine: String,
    val vanillaReady: Boolean,
    val distantHorizonsReady: Boolean,
    val distantHorizonsBackend: String?,
    val distantHorizonsApiVersion: String?,
    val frameAge: Long,
    val horizonStartBlocks: Float,
    val horizonEndBlocks: Float,
    val passCount: Int,
    val skipReason: String?,
) {
    internal fun diagnosticKey(): String = listOf(
        engine,
        distantHorizonsReady,
        distantHorizonsBackend,
        passCount,
        skipReason ?: "rendered",
    ).joinToString("|")

    internal fun runtimeSummary(): String =
        "Unified fog runtime: engine=$engine, vanillaReady=$vanillaReady, dhReady=$distantHorizonsReady, " +
            "backend=${distantHorizonsBackend ?: "none"}, api=${distantHorizonsApiVersion ?: "none"}, " +
            "frameAge=$frameAge, passes=$passCount, horizon=$horizonStartBlocks..$horizonEndBlocks, " +
            "state=${skipReason ?: "rendered"}"
}

object UnifiedFogDebug {

    private val logger = LogManager.getLogger(RENDER_CLIENT_NAME)

    @Volatile
    private var current = inactive()
    private var lastDiagnosticKey: String? = null

    @JvmStatic
    fun state(): UnifiedFogDebugState = current

    internal fun record(state: UnifiedFogDebugState) {
        current = state
        publish(state)
        reportTransition(state)
    }

    internal fun reset(publish: Boolean) {
        val inactive = inactive()
        current = inactive
        lastDiagnosticKey = null
        if (publish) {
            publish(inactive)
        }
    }

    private fun inactive() = UnifiedFogDebugState(
        engine = "Legacy",
        vanillaReady = false,
        distantHorizonsReady = false,
        distantHorizonsBackend = null,
        distantHorizonsApiVersion = null,
        frameAge = 0L,
        horizonStartBlocks = 0f,
        horizonEndBlocks = 0f,
        passCount = 0,
        skipReason = null,
    )

    private fun publish(state: UnifiedFogDebugState) {
        CustomFogRenderBridge.publishDebug(state)
    }

    private fun reportTransition(state: UnifiedFogDebugState) {
        val key = state.diagnosticKey()
        if (key == lastDiagnosticKey) return
        lastDiagnosticKey = key
        logger.info(state.runtimeSummary())
    }
}
