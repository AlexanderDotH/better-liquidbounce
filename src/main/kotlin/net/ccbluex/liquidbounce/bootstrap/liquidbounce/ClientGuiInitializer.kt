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

import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.api.core.ioScope
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine
import net.ccbluex.liquidbounce.deeplearn.ModelManager
import net.ccbluex.liquidbounce.features.marketplace.MarketplaceManager
import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.interop.ClientInteropServer
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.render.FontManager
import net.ccbluex.liquidbounce.render.engine.BlurEffectRenderer
import net.ccbluex.liquidbounce.utils.client.logger
import kotlin.time.measureTime

internal object ClientGuiInitializer {
    suspend fun initialize(dispatcher: CoroutineDispatcher) = withContext(dispatcher) {
        RenderSystem.assertOnRenderThread()
        BrowserBackendManager.init()
        ClientInteropServer.start()
        loadTheme()
        BlurEffectRenderer
        ScreenManager
        ClientBootstrapState.taskManager = createTaskManager()
        loadFonts()
    }

    private suspend fun loadTheme() {
        if (ClientInteropServer.isSkipping) {
            return
        }
        ThemeManager.init()
        ConfigSystem.load(MarketplaceManager)
        ConfigSystem.load(ThemeManager)
        ThemeManager.load()
    }

    private fun createTaskManager() = TaskManager(ioScope).apply {
        BrowserBackendManager.makeDependenciesAvailable(this)
        launchDeepLearningTask()
        launchMarketplaceTask()
    }

    private fun TaskManager.launchDeepLearningTask() {
        launch("Deep Learning") { task ->
            runCatching {
                DeepLearningEngine.init(task)
                ModelManager.load()
                DeepLearningEngine.markInitialized()
            }.onFailure { exception ->
                task.subTasks.clear()
                DeepLearningEngine.markUnavailable()
                logger.info("Failed to initialize deep learning.", exception)
            }
        }
    }

    private fun TaskManager.launchMarketplaceTask() {
        launch("Marketplace") { task ->
            runCatching {
                MarketplaceManager.updateAll(task)
            }.onFailure { exception ->
                logger.error("Failed to update marketplace items.", exception)
            }
            task.isCompleted = true
        }
    }

    private fun loadFonts() {
        val duration = measureTime { FontManager.createGlyphManager() }
        logger.info("Completed loading fonts in ${duration.inWholeMilliseconds} ms.")
        logger.info("Fonts: [ ${FontManager.fontFaces.keys.joinToString()} ]")
    }
}
