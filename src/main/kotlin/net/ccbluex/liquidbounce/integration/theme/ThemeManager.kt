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

import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.api.core.renderScope
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.backend.browser.Browser
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserSettings
import net.ccbluex.liquidbounce.integration.backend.input.InputAcceptor
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import java.io.File
import java.util.concurrent.CompletableFuture

object ThemeManager : Config("theme") {

    private val logger = clientLogger("ThemeManager")

    internal val themesFolder = File(ConfigSystem.rootFolder, "themes")

    val themes: List<Theme>
        field = mutableListOf()
    val themeIds get() = themes.map { theme -> theme.metadata.id }

    private var currentTheme by text("Theme", "liquidbounce").onChanged {
        // Update integration browser
        mc.execute {
            ScreenManager.update()
            ModuleHud.reopen()
            ModuleClickGui.invalidate()
        }
    }

    internal var includedTheme: Theme? = null
        private set
    /**
     * Used for development.
     */
    private var temporaryTheme: Theme? = null

    var theme: Theme?
        get() = temporaryTheme
            ?: themes.find { theme -> theme.metadata.id.equals(currentTheme, true) }
            ?: includedTheme
        set(value) {
            if (value == null) return

            // When external, set as a temporary theme.
            if (value.origin.external) {
                temporaryTheme = value
                val includedTheme = includedTheme ?: return
                currentTheme = includedTheme.metadata.id
            } else {
                temporaryTheme = null
                currentTheme = value.metadata.id
            }
        }

    val isThemeExternal: Boolean
        get() = theme?.origin?.external == true

    private val takesInputHandler = InputAcceptor { mc.gui.screen() != null && mc.gui.screen() !is ChatScreen }

    var shaderEnabled by boolean("Shader", false)
        .onChange { enabled ->
            if (enabled) {
                renderScope.launch {
                    theme?.compileShader()
                    includedTheme?.compileShader()
                }
            }

            return@onChange enabled
        }

    internal val reloader = ResourceManagerReloadListener { resourceManager ->
        themes.forEach { it.onResourceManagerReload(resourceManager) }
        logger.info("Reloaded ${themes.size} themes.")
    }

    init {
        ConfigSystem.root(this)
    }

    suspend fun init() {
        // Load default theme
        includedTheme = Theme.load(Theme.Origin.RESOURCE, File(ClientBuildMetadata.NAME.lowercase()))
    }

    suspend fun load() {
        themes.clear()
        themes.addAll(ThemeCatalogLoader(themesFolder, logger).load(includedTheme))

        ModuleHud.updateThemes()
        if (ClientLifecycleState.isInitialized) {
            ScreenManager.update()
            ModuleHud.reopen()
            ModuleClickGui.invalidate()
        }
    }

    /**
     * Open [Browser] with the given [CustomScreenType] and mark as static if [markAsStatic] is true.
     * This tab will be locked to 60 FPS since it is not input-aware.
     */
    fun openImmediate(
        customScreenType: CustomScreenType? = null,
        markAsStatic: Boolean = false,
        settings: BrowserSettings
    ): Browser {
        val backend = BrowserBackendManager.backend ?: error("Browser backend is not initialized.")

        return backend.createBrowser(
            getScreenLocation(customScreenType, markAsStatic).url,
            settings = settings
        )
    }

    /**
     * Open [Browser] with the given [CustomScreenType] and mark as static if [markAsStatic] is true.
     * This tab will be locked to the highest refresh rate since it is input-aware.
     */
    fun openInputAwareImmediate(
        customScreenType: CustomScreenType? = null,
        markAsStatic: Boolean = false,
        settings: BrowserSettings,
        priority: Short = 10,
        inputAcceptor: InputAcceptor = takesInputHandler
    ): Browser {
        val backend = BrowserBackendManager.backend ?: error("Browser backend is not initialized.")

        return backend.createBrowser(
            getScreenLocation(customScreenType, markAsStatic).url,
            settings = settings,
            priority = priority,
            inputAcceptor = inputAcceptor
        )
    }

    fun updateImmediate(
        browser: Browser?,
        customScreenType: CustomScreenType? = null,
        markAsStatic: Boolean = false
    ) {
        browser?.url = getScreenLocation(customScreenType, markAsStatic).url
    }

    fun getScreenLocation(customScreenType: CustomScreenType? = null, markAsStatic: Boolean = false): ScreenLocation {
        val theme = theme.takeIf { theme ->
            customScreenType == null || theme?.isSupported(customScreenType.routeName) == true
        } ?: includedTheme.takeIf { theme ->
            customScreenType == null || theme?.isSupported(customScreenType.routeName) == true
        } ?: error("No theme supports the route ${customScreenType?.routeName}")

        return ScreenLocation(
            theme,
            theme.getUrl(customScreenType?.routeName, markAsStatic)
        )
    }

    fun loadBackgroundAsync(): CompletableFuture<Unit> = renderScope.future {
        theme?.loadBackgroundImage()
        if (shaderEnabled) {
            theme?.compileShader()
        }
    }

    @Suppress("LongParameterList")
    fun drawBackground(
        context: GuiGraphicsExtractor,
        width: Int, height: Int,
        mouseX: Int, mouseY: Int,
        delta: Float,
    ): Boolean {
        val background = if (shaderEnabled) {
            theme?.backgroundShader
        } else {
            theme?.backgroundImage
        } ?: return false

        try {
            background.draw(context, width, height, mouseX, mouseY, delta)
            return true
        } catch (e: Exception) {
            if (shaderEnabled) {
                logger.warn("Failed to draw theme background, " +
                    "the shader may be invalid, disabling...", e)
                shaderEnabled = false
            } else {
                logger.warn("Failed to draw theme background", e)
            }
            return false
        }
    }

    data class ScreenLocation(val theme: Theme, val url: String)

}
