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

package net.ccbluex.liquidbounce.integration.theme

import com.mojang.blaze3d.platform.NativeImage
import io.netty.handler.codec.http.HttpHeaderNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.api.core.BaseApi
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ThemeColorChangeEvent
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.interop.middleware.AuthConfig
import net.ccbluex.liquidbounce.integration.theme.component.HudComponent
import net.ccbluex.liquidbounce.integration.theme.component.HudComponentFactory.JsonHudComponentFactory
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import okhttp3.Headers
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.Locale

/**
 * A web-based theme loaded from the provided URL.
 *
 * Can be local from [ClientInteropServer] or remote from the internet.
 */
class Theme private constructor(
    val origin: Origin,
    url: String,
    private val routeSupport: MetadataThemeRouteSupport = MetadataThemeRouteSupport(),
) :
    BaseApi(
        url.trimEnd('/'),
        // DO NOT use headersOf(...) because LunarClient uses an outdated version of
        // the OkHttp library.
        defaultHeaders = Headers.Builder()
            .add(
                HttpHeaderNames.COOKIE.toString(),
                "${AuthConfig.AUTH_COOKIE_NAME}=${ClientInteropServer.AUTH_CODE}",
            )
            .build()
    ), Closeable, ResourceManagerReloadListener, ThemeRouteSupport by routeSupport {

    enum class Origin(override val tag: String, val external: Boolean) : Tagged {
        RESOURCE("resource", false),
        LOCAL("local", false),
        MARKETPLACE("marketplace", false),
        REMOTE("remote", true)
    }

    private var _metadata: ThemeMetadata? = null
    val metadata: ThemeMetadata
        get() = requireNotNull(_metadata) { "metadata not loaded" }

    private suspend fun loadMetadata() {
        try {
            _metadata = get<ThemeMetadata>("/metadata.json").apply { checkNotNull() }
            routeSupport.load(metadata)
        } catch (e: Exception) {
            logger.error("Failed to load theme metadata", e)
            throw IllegalStateException("Failed to load theme metadata", e)
        }
    }

    private val componentRuntime = ThemeComponentRuntime(
        loadFactory = { name ->
            get<JsonHudComponentFactory>("/components/${name.lowercase(Locale.US)}.json")
        },
        onColorChanged = { themeId, name, color ->
            EventManager.callEvent(ThemeColorChangeEvent(themeId, name, color))
        },
        unregisterComponent = { component -> EventManager.unregisterEventHandler(component) },
        warn = { message, throwable -> logger.warn(message, throwable) },
    )

    val components: List<HudComponent>
        get() = componentRuntime.components

    val settings: ValueGroup
        get() = componentRuntime.settings

    val colors: ValueGroup
        get() = componentRuntime.colors

    fun addComponent(sourceId: String): HudComponent? = componentRuntime.addComponent(sourceId)

    fun componentCatalog(): List<ComponentCatalogEntry> = componentRuntime.componentCatalog()

    data class ComponentCatalogEntry(
        val name: String,
        val description: String,
        val id: String,
        val singleton: Boolean,
        val canAdd: Boolean,
    )

    private suspend fun loadFonts() {
        for (font in metadata.fonts) {
            runCatching {
                get<InputStream>("/fonts/$font").use { stream ->
                    FontManager.queueFontFromStream(stream)
                }

                logger.info("Loaded font $font for theme ${metadata.name}")
            }.onFailure {
                logger.warn("Failed to load font $font for theme ${metadata.name}", it)
            }
        }
    }

    private suspend fun loadAll() = apply {
        loadMetadata()
        componentRuntime.load(metadata)
        loadFonts()
    }

    var backgroundShader: ThemeBackground? = null
        private set
    private val shaderMutex = Mutex()
    var backgroundImage: ThemeBackground? = null
        private set
    private val imageMutex = Mutex()

    suspend fun compileShader(): Boolean = shaderMutex.withLock {
        if (backgroundShader != null) {
            return true
        }

        // todo: allow multiple backgrounds later on
        val background = metadata.backgrounds.firstOrNull() ?: return false
        if ("frag" !in background.types) {
            // not supported
            return false
        }

        val fragmentShader = runCatching {
            get<String>("/backgrounds/${background.name.lowercase(Locale.US)}.frag")
        }.getOrNull() ?: return false

        withContext(Dispatchers.Minecraft) {
            backgroundShader = ThemeBackground.Shader.build(
                metadata,
                background,
                fragmentShader,
            ).also {
                it.onResourceReload()
            }
        }

        logger.info("Compiled shader background for theme ${metadata.name}")
        return true
    }

    suspend fun loadBackgroundImage(): Boolean = imageMutex.withLock {
        if (backgroundImage != null) {
            return true
        }

        // todo: allow multiple backgrounds later on
        val background = metadata.backgrounds.firstOrNull() ?: return false
        if ("png" !in background.types) {
            // not supported
            return false
        }

        val image = runCatching {
            get<NativeImage>("/backgrounds/${background.name}.png")
        }.getOrNull() ?: return false

        withContext(Dispatchers.Minecraft) {
            backgroundImage = ThemeBackground.Image(metadata, image).also {
                it.onResourceReload()
            }
        }
        logger.info("Loaded background image for theme ${metadata.name}")
        return true
    }

    /**
     * Get the URL to the given page name in the theme.
     */
    fun getUrl(name: String? = null, markAsStatic: Boolean = false): String {
        val baseUrlWithFragment = "$baseUrl/?${AuthConfig.AUTH_CODE_PARAM}=" +
            "${ClientInteropServer.AUTH_CODE}#/${name.orEmpty()}"
        val params = buildList {
            if (origin.external) add("port=${ClientInteropServer.PORT}")
            if (markAsStatic) add("static")
        }.joinToString("&")

        return if (params.isNotEmpty()) "$baseUrlWithFragment?$params" else baseUrlWithFragment
    }

    override fun onResourceManagerReload(manager: ResourceManager) {
        backgroundShader?.onResourceReload()
        backgroundImage?.onResourceReload()
        logger.info("Reloaded theme '${metadata.name}'.")
    }

    override fun close() {
        backgroundShader?.close()
        backgroundImage?.close()
        componentRuntime.close()
    }

    override fun toString() = "Theme(name=${metadata.name}, origin=${origin.tag}, url=$baseUrl)"

    companion object {

        private val logger = clientLogger("Theme")

        @JvmStatic
        suspend fun load(url: String) = Theme(Origin.REMOTE, url).loadAll()

        @JvmStatic
        suspend fun load(origin: Origin, file: File) = Theme(
            origin,
            url = "${ClientInteropServer.url}/${origin.tag}/${file.invariantSeparatorsPath}/"
        ).loadAll()
    }

}
