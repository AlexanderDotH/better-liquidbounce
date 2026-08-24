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

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistantHorizonsPublicApiContractTest {

    @Test
    fun `DH API is compile only and is never bundled`() {
        val build = read(BUILD_FILE)

        assertTrue(build.contains("compileOnly(\"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(build.contains("include(\"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(build.contains("api(\"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(build.contains("runtimeOnly(\"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
    }

    @Test
    fun `typed public events own render state depth cleanup and resize lifecycle`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = read(DEPTH_PROVIDER)
        val config = read(MIXIN_CONFIG)

        assertTrue(bridge.contains("DhApiBeforeRenderSetupEvent"))
        assertTrue(bridge.contains("DhApiBeforeRenderCleanupEvent"))
        assertTrue(bridge.contains("DhApiBeforeFogRenderEvent"))
        assertFalse(bridge.contains("DhApiBeforeTextureClearEvent"))
        assertTrue(bridge.contains("DhApiBeforeColorDepthTextureCreatedEvent"))
        assertTrue(bridge.contains("DhApiLevelLoadEvent"))
        assertTrue(bridge.contains("DhApiLevelUnloadEvent"))
        assertTrue(bridge.contains("DhApi.events.bind"))
        assertTrue(provider.contains("DistantHorizonsPublicEventBridge"))
        assertTrue(provider.contains("Class.forName"))
        assertFalse(config.contains("MixinDistantHorizonsDepthCapture"))
        assertFalse(config.contains("MixinDistantHorizonsRenderParam"))
        assertFalse(config.contains("MixinDistantHorizonsFogRenderer"))
    }

    @Test
    fun `only API 7 point 1 texture capabilities use optional reflection`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)

        assertTrue(bridge.contains("getRenderingEngine"))
        assertTrue(bridge.contains("getDhDepthTextureGlId"))
        assertTrue(bridge.contains("getDhDepthTextureBlazeWrapper"))
        assertTrue(bridge.contains("renderProxy.dhDepthTextureId"))
        assertTrue(bridge.contains("getApiMajorVersion"))
        assertTrue(bridge.contains("getModVersion"))
    }

    @Test
    fun `resize world change and shutdown release every owned DH resource`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = read(DEPTH_PROVIDER)

        assertTrue(bridge.contains("DhApi.events.unbind"))
        assertTrue(provider.contains("override fun onResize()"))
        assertTrue(provider.contains("override fun onWorldChanged()"))
        assertTrue(provider.contains("integration?.close()"))
        assertTrue(provider.contains("capturedDepth?.close()"))
        assertTrue(provider.contains("borrowedTexture?.close()"))
        assertTrue(provider.contains("BorrowedRawDepthTexture(") && provider.contains(": AutoCloseable"))
        assertTrue(provider.contains("ClientShutdownEvent"))
        assertTrue(provider.contains("warningReported"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val BUILD_FILE = "build.gradle.kts"
        const val PUBLIC_EVENT_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/DistantHorizonsPublicEventBridge.kt"
        const val DEPTH_PROVIDER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/DistantHorizonsDepthTextureProvider.kt"
        const val MIXIN_CONFIG = "src/main/resources/liquidbounce.mixins.json"
    }
}
