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
package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistantHorizonsPublicApiContractTest {

    @Test
    fun `DH API is compile only and is never bundled`() {
        val dependencies = read(GAME_DEPENDENCIES_FILE)

        assertTrue(dependencies.contains("add(\"compileOnly\", \"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(dependencies.contains("add(\"include\", \"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(dependencies.contains("add(\"api\", \"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
        assertFalse(dependencies.contains("add(\"runtimeOnly\", \"maven.modrinth:DistantHorizonsApi:7.0.0\")"))
    }

    @Test
    fun `typed public events own render state depth cleanup and resize lifecycle`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = readProviderImplementation()
        val config = read(MIXIN_CONFIG)

        assertTrue(bridge.contains("DhApiBeforeRenderSetupEvent"))
        assertTrue(bridge.contains("DhApiBeforeRenderCleanupEvent"))
        assertTrue(bridge.contains("DhApiBeforeFogRenderEvent"))
        assertTrue(bridge.contains("DhApiBeforeTextureClearEvent"))
        assertTrue(bridge.contains("sink.onBeforeTextureClear"))
        assertTrue(bridge.contains("DhApiBeforeColorDepthTextureCreatedEvent"))
        assertTrue(bridge.contains("DhApiLevelLoadEvent"))
        assertTrue(bridge.contains("DhApiLevelUnloadEvent"))
        assertTrue(bridge.contains("DhApi.events.bind"))
        assertTrue(provider.contains("DistantHorizonsPublicEventBridge"))
        assertTrue(provider.contains("Class.forName"))
        assertTrue(provider.contains("fun resolveRecent("))
        assertTrue(provider.contains("lifecycle.recentCapture"))
        assertTrue(provider.contains("hasCompatibleAspect"))
        assertFalse(config.contains("MixinDistantHorizonsDepthCapture"))
        assertFalse(config.contains("MixinDistantHorizonsRenderParam"))
        assertFalse(config.contains("MixinDistantHorizonsFogRenderer"))
    }

    @Test
    fun `only API 7 point 1 texture capabilities use optional reflection`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val api71Capability = read(API_71_DEPTH_CAPABILITY)

        assertTrue(api71Capability.contains("getRenderingEngine"))
        assertTrue(api71Capability.contains("getDhDepthTextureGlId"))
        assertTrue(api71Capability.contains("getDhDepthTextureBlazeWrapper"))
        assertTrue(bridge.contains("renderProxy.dhDepthTextureId"))
        assertTrue(bridge.contains("getApiMajorVersion"))
        assertTrue(bridge.contains("getModVersion"))
    }

    @Test
    fun `resize world change and shutdown release every owned DH resource`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = readProviderImplementation()

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

    private fun readProviderImplementation(): String = listOf(
        DEPTH_PROVIDER,
        DEPTH_CAPTURE,
        DEPTH_RESOURCES,
        INTEGRATION_LIFECYCLE,
    ).joinToString(separator = "\n", transform = ::read)

    private companion object {
        const val GAME_DEPENDENCIES_FILE = "gradle/game-dependencies.gradle.kts"
        const val PUBLIC_EVENT_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsPublicEventBridge.kt"
        const val API_71_DEPTH_CAPABILITY =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/Api71DepthCapability.kt"
        const val DEPTH_PROVIDER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/" +
                "DistantHorizonsDepthTextureProvider.kt"
        const val DEPTH_CAPTURE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsDepthCapture.kt"
        const val DEPTH_RESOURCES =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsDepthResources.kt"
        const val INTEGRATION_LIFECYCLE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/" +
                "DistantHorizonsIntegrationLifecycle.kt"
        const val MIXIN_CONFIG = "src/main/resources/liquidbounce.mixins.json"
    }
}
