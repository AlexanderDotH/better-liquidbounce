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
import com.seibel.distanthorizons.api.DhApi
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeColorDepthTextureCreatedEvent
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeFogRenderEvent
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderSetupEvent
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent
import com.seibel.distanthorizons.api.methods.events.interfaces.IDhApiEvent
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiTextureCreatedParam
import com.seibel.distanthorizons.api.objects.DhApiResult
import java.lang.reflect.Method
import java.util.ArrayDeque

/**
 * Typed DH 7.0 event adapter. This class is loaded reflectively only after DH itself is present.
 * Reflection below is intentionally restricted to the backend-specific texture methods introduced in API 7.1.
 */
internal class DistantHorizonsPublicEventBridge private constructor(
    private val sink: DistantHorizonsPublicEventSink,
) : DistantHorizonsPublicIntegration {

    private val unbindActions = ArrayDeque<() -> Unit>()
    private val depthCapability = DistantHorizonsDepthCapability.discover()

    private val renderSetup = object : DhApiBeforeRenderSetupEvent() {
        override fun beforeSetup(event: DhApiEventParam<DhApiRenderParam>) {
            sink.onRenderSetup(event.value.toPublicParam())
        }
    }
    private val renderCleanup = object : DhApiBeforeRenderCleanupEvent() {
        override fun beforeCleanup(event: DhApiEventParam<DhApiRenderParam>) {
            sink.onBeforeRenderCleanup(event.value.toPublicParam())
        }
    }
    private val fogRender = object : DhApiBeforeFogRenderEvent() {
        override fun beforeRender(event: DhApiCancelableEventParam<EventParam>) {
            if (sink.shouldSuppressNativeFog()) {
                event.cancelEvent()
            }
        }
    }
    private val resize = object : DhApiBeforeColorDepthTextureCreatedEvent() {
        override fun onResize(event: DhApiEventParam<DhApiTextureCreatedParam>) {
            sink.onResize()
        }
    }
    private val levelLoad = object : DhApiLevelLoadEvent() {
        override fun onLevelLoad(event: DhApiEventParam<EventParam>) {
            sink.onWorldChanged()
        }
    }
    private val levelUnload = object : DhApiLevelUnloadEvent() {
        override fun onLevelUnload(event: DhApiEventParam<EventParam>) {
            sink.onWorldChanged()
        }
    }

    override val apiVersion: String =
        "${DhApi.getApiMajorVersion()}.${DhApi.getApiMinorVersion()}.${DhApi.getApiPatchVersion()}"
    override val modVersion: String = DhApi.getModVersion()

    init {
        runCatching {
            bind(DhApiBeforeRenderSetupEvent::class.java, renderSetup)
            bind(DhApiBeforeRenderCleanupEvent::class.java, renderCleanup)
            bind(DhApiBeforeFogRenderEvent::class.java, fogRender)
            bind(DhApiBeforeColorDepthTextureCreatedEvent::class.java, resize)
            bind(DhApiLevelLoadEvent::class.java, levelLoad)
            bind(DhApiLevelUnloadEvent::class.java, levelUnload)
        }.onFailure {
            close()
            throw it
        }
    }

    override fun depthTexture(): DistantHorizonsDepthFetch {
        val renderProxy = DhApi.Delayed.renderProxy ?: return DistantHorizonsDepthFetch()
        return depthCapability.fetch(renderProxy)
    }

    override fun close() {
        while (unbindActions.isNotEmpty()) {
            runCatching { unbindActions.removeFirst().invoke() }
        }
    }

    private fun <E : IDhApiEvent<*>> bind(eventClass: Class<E>, handler: E) {
        DhApi.events.bind(eventClass, handler)
        unbindActions.addFirst {
            @Suppress("UNCHECKED_CAST")
            DhApi.events.unbind(eventClass, handler.javaClass as Class<out IDhApiEvent<*>>)
        }
    }

    companion object {
        @JvmStatic
        fun install(sink: DistantHorizonsPublicEventSink): DistantHorizonsPublicIntegration =
            DistantHorizonsPublicEventBridge(sink)
    }
}

private fun DhApiRenderParam.toPublicParam() = DistantHorizonsPublicRenderParam(
    inverseMvmProjection = dhInverseMvmProjectionMatrix.getValuesAsArray(),
    nearClipPlane = nearClipPlane,
    farClipPlane = farClipPlane,
)

private class DistantHorizonsDepthCapability private constructor(
    private val api71: Api71DepthCapability?,
) {

    fun fetch(renderProxy: IDhApiRenderProxy): DistantHorizonsDepthFetch =
        api71?.fetch(renderProxy) ?: fetchStableOpenGl(renderProxy)

    private fun fetchStableOpenGl(renderProxy: IDhApiRenderProxy): DistantHorizonsDepthFetch {
        val renderingApi = runCatching { renderProxy.renderingApi }.getOrNull()
            ?: return DistantHorizonsDepthFetch()
        if (renderingApi.name != DistantHorizonsBackend.OPEN_GL.name) {
            return DistantHorizonsDepthFetch(
                backend = renderingApi.name,
                unsupportedReason = "Distant Horizons $renderingApi depth is not exposed by API 7.0",
            )
        }

        val textureId = renderProxy.dhDepthTextureId.successfulPayload() ?: return DistantHorizonsDepthFetch(
            backend = DistantHorizonsBackend.OPEN_GL.name,
        )
        return DistantHorizonsDepthFetch(
            snapshot = DistantHorizonsDepthSnapshot(
                convention = DistantHorizonsDepthConvention.OPEN_GL,
                backend = DistantHorizonsBackend.OPEN_GL,
                openGlTextureId = textureId,
            ),
            backend = DistantHorizonsBackend.OPEN_GL.name,
        )
    }

    companion object {
        fun discover() = DistantHorizonsDepthCapability(
            api71 = runCatching(Api71DepthCapability::discover).getOrNull(),
        )
    }
}

private class Api71DepthCapability(
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

private fun DhApiResult<Int>.successfulPayload(): Int? = payload.takeIf { success && it > 0 }

private inline fun <reified T> Any.successfulPayload(): T? {
    val result = this as? DhApiResult<*> ?: return null
    if (!result.success) return null
    return result.payload as? T
}
