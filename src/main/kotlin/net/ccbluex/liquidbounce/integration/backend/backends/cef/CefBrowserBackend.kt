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
package net.ccbluex.liquidbounce.integration.backend.backends.cef

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.integration.backend.BrowserAccelerationFlags
import net.ccbluex.liquidbounce.integration.backend.BrowserBackend
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserSettings
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserState
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserViewport
import net.ccbluex.liquidbounce.integration.backend.input.InputAcceptor
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.mcef.MCEFAccelerationSupport
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.kotlin.sortedInsert

/**
 * Uses a modified fork of the JCEF library browser backend made for Minecraft.
 * This browser backend is based on Chromium and is the most advanced browser backend.
 * JCEF is available through the MCEF library, which provides a Minecraft compatible version of JCEF.
 *
 * @see <a href="https://github.com/CCBlueX/java-cef/">JCEF</a>
 * @see <a href="https://github.com/CCBlueX/mcef/">MCEF</a>
 *
 * @author Izuna <izuna.seikatsu@ccbluex.net>
 */
class CefBrowserBackend : BrowserBackend, EventListener {

    private val mcefFolder = ConfigSystem.rootFolder.resolve("mcef")
    private val librariesFolder = mcefFolder.resolve("libraries")
    private val cacheFolder = mcefFolder.resolve("cache")
    private val cacheManager = CefCacheManager(cacheFolder)
    private val dependencyInstaller = CefDependencyInstaller(librariesFolder, cacheManager)

    override val isInitialized: Boolean
        get() = MCEF.INSTANCE.isInitialized
    override var browsers = mutableListOf<CefBrowser>()
    override var accelerationFlags = BrowserAccelerationFlags.UNSUPPORTED

    override fun makeDependenciesAvailable(taskManager: TaskManager, whenAvailable: () -> Unit) {
        dependencyInstaller.makeAvailable(taskManager, whenAvailable)
    }

    fun cleanup() {
        cacheManager.cleanup()
    }

    override fun start() {
        if (!MCEF.INSTANCE.isInitialized) {
            MCEF.INSTANCE.initialize()
            CefLifecycleHandlerInstaller.install(::markInitialized, ::updateStateForBrowser)
        }

        if (shouldDisableCefGpuAcceleration()) {
            accelerationFlags = BrowserAccelerationFlags.UNSUPPORTED
            return
        }

        val support = MCEFAccelerationSupport.getAccelerationSupport()
        accelerationFlags = if (support.isSupported) {
            BrowserAccelerationFlags(isSupported = true, isBeta = support.isBeta)
        } else {
            BrowserAccelerationFlags.UNSUPPORTED
        }
    }

    override fun stop() {
        MCEF.INSTANCE.shutdown()
        MCEF.INSTANCE.settings.cacheDirectory?.deleteRecursively()
    }

    override fun update() {
        if (MCEF.INSTANCE.isInitialized) {
            try {
                MCEF.INSTANCE.app.handle.N_DoMessageLoopWork()
                syncPendingBrowserInitialization()
            } catch (e: Exception) {
                logger.error("Failed to draw browser globally", e)
            }
        }
    }

    private fun syncPendingBrowserInitialization() {
        for (browser in browsers) {
            if (browser.isInitialized) {
                continue
            }

            val identifier = browser.browserApi.identifier
            if (identifier != -1) {
                browser.isInitialized = true
            }
        }
    }

    override val supportsIncognito = true

    override fun createBrowser(
        url: String,
        position: BrowserViewport,
        settings: BrowserSettings,
        priority: Short,
        incognito: Boolean,
        inputAcceptor: InputAcceptor?
    ) = CefBrowser(this, url, position, settings, priority, incognito, inputAcceptor)

    internal fun registerBrowser(browser: CefBrowser) {
        addBrowser(browser)
    }

    private fun addBrowser(browser: CefBrowser) {
        browsers.sortedInsert(browser, CefBrowser::priority)
    }

    internal fun removeBrowser(browser: CefBrowser) {
        browsers.remove(browser)
    }

    fun getBrowserByApi(apiInstance: org.cef.browser.CefBrowser) = browsers.find { browser ->
        browser.browserApi == apiInstance
            || (apiInstance.identifier != -1 && browser.browserApi.identifier == apiInstance.identifier)
    }

    private fun markInitialized(apiInstance: org.cef.browser.CefBrowser) {
        val browser = getBrowserByApi(apiInstance)
        if (browser != null) {
            if (!browser.isInitialized) {
                browser.isInitialized = true
            }
        } else {
            logger.warn("[CefBrowser-${apiInstance.hashCode()}] Browser Instance not present in BrowserManager")
        }
    }

    private fun updateStateForBrowser(apiInstance: org.cef.browser.CefBrowser, state: BrowserState) {
        val browser = getBrowserByApi(apiInstance)
        if (browser != null) {
            browser.state = state
        } else {
            logger.warn("[CefBrowser-${apiInstance.hashCode()}] Browser Instance not present in BrowserManager")
        }
    }

}
