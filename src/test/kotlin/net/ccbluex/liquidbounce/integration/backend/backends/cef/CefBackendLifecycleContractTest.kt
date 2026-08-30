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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class CefBackendLifecycleContractTest {

    @Test
    fun `dependency preparation preserves compatibility validation download and callback order`() {
        val source = source("CefDependencyInstaller.kt")

        assertTrue(source.contains("if (MCEF.INSTANCE.isInitialized) return"))
        assertTrue(source.contains("if (!resourceManager.isSystemCompatible) throw JcefIsntCompatible()"))
        assertTrue(source.indexOf("HashValidator.validateFolder") < source.indexOf("requiresDownload()"))
        assertTrue(source.contains("resourceManager.registerProgressListener(MCEFProgressForwarder(task))"))
        assertTrue(source.indexOf("resourceManager.downloadJcef()") < source.indexOf("mc.execute(whenAvailable)"))
    }

    @Test
    fun `backend initializes MCEF before registering lifecycle handlers`() {
        val source = source("CefBrowserBackend.kt")

        assertTrue(source.indexOf("MCEF.INSTANCE.initialize()") < source.indexOf("CefLifecycleHandlerInstaller.install"))
        assertTrue(source.contains("override val supportsIncognito = true"))
    }

    @Test
    fun `session cache remains outside the instance and clears singleton files`() {
        val source = source("CefCacheManager.kt")

        assertTrue(source.contains(".cache\", \"liquidbounce-mcef"))
        assertTrue(source.contains("SingletonLock"))
        assertTrue(source.contains("SingletonCookie"))
        assertTrue(source.contains("SingletonSocket"))
    }

    private fun source(file: String) = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/integration/backend/backends/cef/$file"
    ).readText()
}
