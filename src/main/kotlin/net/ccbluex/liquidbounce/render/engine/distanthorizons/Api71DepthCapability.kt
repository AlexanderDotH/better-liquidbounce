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

@file:JvmName("DistantHorizonsPublicEventBridgeKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import com.mojang.blaze3d.textures.GpuTextureView
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy
import com.seibel.distanthorizons.api.objects.DhApiResult
import java.lang.reflect.Method

internal class Api71DepthCapability(
    private val getRenderingEngine: Method,
    private val getOpenGlDepthTexture: Method,
    private val getBlazeDepthTexture: Method,
) {

    fun fetch(renderProxy: IDhApiRenderProxy): DistantHorizonsDepthFetch {
        val engineName = getRenderingEngine.invoke(renderProxy).toString()
        val backend = DistantHorizonsBackend.entries.firstOrNull { it.name == engineName }
            ?: return DistantHorizonsDepthFetch(
                backend = engineName,
                unsupportedReason = "Unsupported Distant Horizons rendering engine: $engineName",
            )

        return when (backend) {
            DistantHorizonsBackend.OPEN_GL -> openGlDepth(renderProxy, backend)
            DistantHorizonsBackend.BLAZE_3D -> blazeDepth(renderProxy, backend)
        }
    }

    private fun openGlDepth(
        renderProxy: IDhApiRenderProxy,
        backend: DistantHorizonsBackend,
    ): DistantHorizonsDepthFetch {
        val textureId = getOpenGlDepthTexture.invoke(renderProxy).successfulPayload<Int>()
            ?: return DistantHorizonsDepthFetch(backend = backend.name)
        return DistantHorizonsDepthFetch(
            snapshot = DistantHorizonsDepthSnapshot(
                convention = DistantHorizonsDepthConvention.OPEN_GL,
                backend = backend,
                openGlTextureId = textureId,
            ),
            backend = backend.name,
        )
    }

    private fun blazeDepth(
        renderProxy: IDhApiRenderProxy,
        backend: DistantHorizonsBackend,
    ): DistantHorizonsDepthFetch {
        val wrapper = getBlazeDepthTexture.invoke(renderProxy).successfulPayload<Any>()
            ?: return DistantHorizonsDepthFetch(backend = backend.name)
        val textureView = wrapper.javaClass.getMethod("getTextureView").invoke(wrapper) as? GpuTextureView
            ?: return DistantHorizonsDepthFetch(
                backend = backend.name,
                unsupportedReason = "DH Blaze depth wrapper does not expose a compatible texture view",
            )
        return DistantHorizonsDepthFetch(
            snapshot = DistantHorizonsDepthSnapshot(
                convention = DistantHorizonsDepthConvention.BLAZE_3D,
                backend = backend,
                blazeTextureView = textureView,
            ),
            backend = backend.name,
        )
    }

    companion object {
        fun discover(): Api71DepthCapability {
            val renderProxy = IDhApiRenderProxy::class.java
            return Api71DepthCapability(
                getRenderingEngine = renderProxy.getMethod("getRenderingEngine"),
                getOpenGlDepthTexture = renderProxy.getMethod("getDhDepthTextureGlId"),
                getBlazeDepthTexture = renderProxy.getMethod("getDhDepthTextureBlazeWrapper"),
            )
        }
    }
}

internal fun DhApiResult<Int>.successfulPayload(): Int? = payload.takeIf { success && it > 0 }

private inline fun <reified T> Any.successfulPayload(): T? {
    val result = this as? DhApiResult<*> ?: return null
    if (!result.success) return null
    return result.payload as? T
}
