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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.common.ClientLifecycleState
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.features.baritone.BaritoneIntegration
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.task.TaskProgressScreen
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

internal object ClientLifecycle {
    fun start() {
        runCatching {
            logStartupEnvironment()
            EventManager
            registerResourceReloaders()
        }.onFailure {
            ErrorHandler.fatal(it, additionalMessage = "Client start")
        }
    }

    fun enforceTaskScreen(event: ScreenEvent) {
        val taskManager = ClientBootstrapState.taskManager ?: return
        if (taskManager.isCompleted || event.screen is TaskProgressScreen) {
            return
        }
        event.cancelEvent()
        mc.gui.setScreen(TaskProgressScreen("Loading Required Libraries", taskManager))
    }

    fun shutdown() {
        if (!ClientLifecycleState.isInitialized) {
            return
        }
        ClientLifecycleState.isInitialized = false
        logger.info("Shutting down client...")
        BaritoneIntegration.shutdown()
        ChunkScanner.stopThread()
        FontManager.closeGlyphManager()
        EventManager.unregisterAll()
        ioScope.launch { ClientInteropServer.stop() }
        ConfigSystem.storeAll()
        BrowserBackendManager.stop()
    }

    private fun logStartupEnvironment() {
        logger.info("Launching $CLIENT_NAME v${LiquidBounceClientConfig.clientVersion} by $CLIENT_AUTHOR")
        logger.info(
            "Client Version: ${LiquidBounceClientConfig.clientVersion} " +
                "(${LiquidBounceClientConfig.clientCommit})"
        )
        logger.info("Client Branch: ${LiquidBounceClientConfig.clientBranch}")
        logger.info("Operating System: ${System.getProperty("os.name")} (${System.getProperty("os.version")})")
        logger.info("Java Version: ${System.getProperty("java.version")}")
        logger.info("Screen Resolution: ${mc.window.screenWidth}x${mc.window.screenHeight}")
        logger.info("Refresh Rate: ${mc.window.refreshRate} Hz")
    }

    private fun registerResourceReloaders() {
        val resourceManager = mc.resourceManager
        if (resourceManager is ReloadableResourceManager) {
            resourceManager.registerReloadListener(ClientResourceReloader)
            resourceManager.registerReloadListener(ThemeManager.reloader)
            return
        }
        logger.warn("Failed to register resource reloader!")
        ClientInitializer.initialize(Dispatchers.Default, Dispatchers.Minecraft).thenRun {
            ThemeManager.reloader.onResourceManagerReload(resourceManager)
        }
    }
}

private object ClientResourceReloader : PreparableReloadListener {
    override fun reload(
        store: PreparableReloadListener.SharedState,
        prepareExecutor: Executor,
        synchronizer: PreparableReloadListener.PreparationBarrier,
        applyExecutor: Executor,
    ): CompletableFuture<Void> = synchronizer.wait(net.minecraft.util.Unit.INSTANCE).thenCompose {
        ClientInitializer.initialize(
            workerDispatcher = prepareExecutor.asCoroutineDispatcher(),
            renderThreadDispatcher = applyExecutor.asCoroutineDispatcher(),
        )
    }

    override fun getName() = CLIENT_NAME
}
