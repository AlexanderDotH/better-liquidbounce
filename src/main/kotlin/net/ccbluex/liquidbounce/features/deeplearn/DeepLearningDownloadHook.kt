/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.deeplearn

import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine

object DeepLearningDownloadHook {
    @JvmStatic
    fun updateProgress(url: String?, bytesRead: Long, contentLength: Long, done: Boolean) {
        val mainTask = DeepLearningEngine.task ?: return
        val currentUrl = url ?: return
        val task = mainTask.getOrCreateFileTask(currentUrl)
        task.update(bytesRead, contentLength)
        if (done) task.isCompleted = true
    }
}
