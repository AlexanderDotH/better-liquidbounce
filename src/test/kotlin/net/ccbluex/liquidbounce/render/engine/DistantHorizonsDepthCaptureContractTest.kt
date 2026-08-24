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
import kotlin.test.assertTrue

class DistantHorizonsDepthCaptureContractTest {

    @Test
    fun `DH depth is copied from the public pre cleanup event`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = read(DEPTH_PROVIDER)
        val config = read(MIXIN_CONFIG)

        assertTrue(bridge.contains("DhApiBeforeRenderCleanupEvent"))
        assertTrue(bridge.contains("onBeforeRenderCleanup"))
        assertTrue(provider.contains("captureBeforeCleanup"))
        assertTrue(provider.contains("capturedDepth"))
        assertTrue(provider.contains("GpuTexture.USAGE_COPY_SRC"))
        assertTrue(provider.contains("GpuTexture.USAGE_COPY_DST"))
        assertTrue(provider.contains("copyTextureToTexture"))
        assertTrue(!config.contains("compat.distanthorizons.MixinDistantHorizonsDepthCapture"))
    }

    @Test
    fun `DH render API inverse matrix is captured from typed public parameters`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val api = read(RENDER_API)
        val config = read(MIXIN_CONFIG)

        assertTrue(bridge.contains("DhApiRenderParam"))
        assertTrue(bridge.contains("dhInverseMvmProjectionMatrix"))
        assertTrue(bridge.contains("onRenderSetup"))
        assertTrue(api.contains("captureRenderParam"))
        assertTrue(bridge.contains("getValuesAsArray"))
        assertTrue(api.contains("nearClipPlane"))
        assertTrue(api.contains("farClipPlane"))
        assertTrue(!config.contains("compat.distanthorizons.MixinDistantHorizonsRenderParam"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val PUBLIC_EVENT_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/DistantHorizonsPublicEventBridge.kt"
        const val DEPTH_PROVIDER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/DistantHorizonsDepthTextureProvider.kt"
        const val RENDER_API =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/DistantHorizonsRenderApi.kt"
        const val MIXIN_CONFIG = "src/main/resources/liquidbounce.mixins.json"
    }
}
