/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

private val logger = LoggerFactory.getLogger(DistantHorizonsDepthTextureProvider::class.java)

internal fun DistantHorizonsDepthTextureProvider.ensureIntegration() {
    if (installAttempted) return
    installAttempted = true
    val loader = DistantHorizonsDepthTextureProvider::class.java.classLoader
    apiInstalled = runCatching { Class.forName(DH_API_CLASS, false, loader) }.isSuccess
    if (!apiInstalled) return

    runCatching {
        val bridge = Class.forName(PUBLIC_BRIDGE_CLASS, true, loader)
        val install = bridge.getMethod("install", DistantHorizonsPublicEventSink::class.java)
        install.invoke(null, this) as DistantHorizonsPublicIntegration
    }.onSuccess { installed ->
        integration = installed
        apiVersion = installed.apiVersion
        modVersion = installed.modVersion
    }.onFailure { failure ->
        markUnsupported("Unable to register Distant Horizons public render events", unwrap(failure))
    }
}

internal fun DistantHorizonsDepthTextureProvider.eventFrameToken(): Long {
    if (externallyTokenedFrames) return requireNotNull(lifecycle.currentFrameToken())
    legacyEventFrameToken++
    lifecycle.beginFrame(legacyEventFrameToken)
    return legacyEventFrameToken
}

internal fun DistantHorizonsDepthTextureProvider.markUnsupported(detail: String, failure: Throwable? = null) {
    capabilitySupported = false
    statusDetail = detail
    if (warningReported) return
    warningReported = true
    if (failure == null) logger.warn(detail) else logger.warn(detail, failure)
}

internal fun DistantHorizonsDepthTextureProvider.invalidateDepth() {
    capturedDepth?.close()
    capturedDepth = null
    borrowedTexture?.close()
    borrowedTexture = null
    openGlFrameBufferCache.invalidate()
    lifecycle.invalidate()
    textureClearPrimed = false
    currentRenderState = null
    completedRenderState = null
}

internal fun DistantHorizonsDepthTextureProvider.shutdown() {
    integration?.close()
    integration = null
    invalidateDepth()
    DistantHorizonsRenderApi.invalidate()
    lifecycle.reset()
    installAttempted = false
    apiInstalled = false
    capabilitySupported = true
    apiVersion = null
    modVersion = null
    backend = null
    statusDetail = null
    warningReported = false
    externallyTokenedFrames = false
    legacyEventFrameToken = 0L
    textureClearPrimed = false
    currentRenderState = null
    completedRenderState = null
}

private fun unwrap(failure: Throwable): Throwable =
    (failure as? InvocationTargetException)?.targetException ?: failure

private const val DH_API_CLASS = "com.seibel.distanthorizons.api.DhApi"
private const val PUBLIC_BRIDGE_CLASS =
    "net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsPublicEventBridge"
