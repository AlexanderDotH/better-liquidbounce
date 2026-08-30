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

import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.formatAsCapacity
import java.io.File
import java.nio.file.Path

internal class CefCacheManager(private val legacyCacheFolder: File) {

    fun prepareSessionDirectory(gameDirectory: File): File {
        val instanceId = gameDirectory.absolutePath.hashCode().toUInt().toString(16)
        val sessionId = System.currentTimeMillis().toString(16)
        val cacheDirectory = Path.of(
            System.getProperty("user.home"), ".cache", "liquidbounce-mcef", instanceId, sessionId,
        ).toFile()
        cacheDirectory.mkdirs()
        CEF_SINGLETON_FILES.forEach { name -> cacheDirectory.resolve(name).delete() }
        cacheDirectory.deleteOnExit()
        logger.info("Using JCEF cache directory: ${cacheDirectory.absolutePath}")
        return cacheDirectory
    }

    fun cleanup() {
        cleanupRoot(Path.of(System.getProperty("user.home"), ".cache", "liquidbounce-mcef"))
        cleanupRoot(legacyCacheFolder.toPath())
    }

    private fun cleanupRoot(root: Path) {
        if (!root.toFile().exists()) return
        runCatching { removeExpiredDirectories(root) }
            .onFailure { logger.error("Failed to clean up old JCEF cache directories under $root", it) }
            .onSuccess { size ->
                if (size > 0) logger.info("Cleaned up ${size.formatAsCapacity()} JCEF cache directories under $root")
            }
    }

    private fun removeExpiredDirectories(root: Path): Long = root.toFile().listFiles { file ->
        file.isDirectory && System.currentTimeMillis() - file.lastModified() > CACHE_CLEANUP_THRESHOLD
    }?.sumOf { file ->
        runCatching {
            val size = file.walkTopDown().sumOf(File::length)
            file.deleteRecursively()
            size
        }.onFailure { logger.error("Failed to clean up old cache directory", it) }.getOrDefault(0)
    } ?: 0
}

private const val CACHE_CLEANUP_THRESHOLD = 1000 * 60 * 60 * 24 * 7
private val CEF_SINGLETON_FILES = arrayOf("SingletonLock", "SingletonCookie", "SingletonSocket")
