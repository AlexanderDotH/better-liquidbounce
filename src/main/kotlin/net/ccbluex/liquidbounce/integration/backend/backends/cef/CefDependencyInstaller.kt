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
package net.ccbluex.liquidbounce.integration.backend.backends.cef

import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.integration.task.MCEFProgressForwarder
import net.ccbluex.liquidbounce.integration.task.TaskManager
import net.ccbluex.liquidbounce.mcef.MCEF
import net.ccbluex.liquidbounce.utils.client.error.ErrorHandler
import net.ccbluex.liquidbounce.utils.client.error.QuickFix
import net.ccbluex.liquidbounce.utils.client.error.errors.JcefIsntCompatible
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.validation.HashValidator
import java.io.File

internal class CefDependencyInstaller(
    private val librariesFolder: File,
    private val cacheManager: CefCacheManager,
) {

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    fun makeAvailable(taskManager: TaskManager, whenAvailable: () -> Unit) {
        cacheManager.cleanup()
        if (MCEF.INSTANCE.isInitialized) return

        configureSettings()
        val resourceManager = MCEF.INSTANCE.newResourceManager()
        if (!resourceManager.isSystemCompatible) throw JcefIsntCompatible()
        HashValidator.validateFolder(resourceManager.commitDirectory)

        if (resourceManager.requiresDownload()) {
            taskManager.launch("MCEF") { task ->
                resourceManager.registerProgressListener(MCEFProgressForwarder(task))
                runCatching {
                    resourceManager.downloadJcef()
                    mc.execute(whenAvailable)
                }.onFailure { error ->
                    ErrorHandler.fatal(
                        error = error,
                        quickFix = QuickFix.DOWNLOAD_JCEF_FAILED,
                        additionalMessage = "Downloading jcef",
                    )
                }
            }
        } else {
            whenAvailable()
        }
    }

    private fun configureSettings() {
        val disableGpuAcceleration = shouldDisableCefGpuAcceleration()
        val cefSwitches = CefSwitches.forConfiguration(disableGpuAcceleration)
        MCEF.INSTANCE.settings.apply {
            userAgent = HttpClient.DEFAULT_AGENT
            okHttpClient = HttpClient.browserClient
            cacheDirectory = cacheManager.prepareSessionDirectory(mc.gameDirectory)
            librariesDirectory = librariesFolder
            appendCefSwitches(*cefSwitches.toTypedArray())
        }
        logger.info("Starting JCEF with switches: ${cefSwitches.joinToString(" ")}")
        if (disableGpuAcceleration) logger.warn("Starting JCEF with Chromium GPU acceleration disabled.")
    }
}
