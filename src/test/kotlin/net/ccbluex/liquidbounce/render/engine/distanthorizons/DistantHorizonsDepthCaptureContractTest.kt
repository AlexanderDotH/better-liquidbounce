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

class DistantHorizonsDepthCaptureContractTest {

    @Test
    fun `DH depth is copied while the public OpenGL texture is still active`() {
        val bridge = read(PUBLIC_EVENT_BRIDGE)
        val provider = read(DEPTH_PROVIDER) + read(DEPTH_CAPTURE) + read(DEPTH_RESOURCES)
        val config = read(MIXIN_CONFIG)

        assertTrue(bridge.contains("DhApiBeforeRenderCleanupEvent"))
        assertTrue(bridge.contains("onBeforeRenderCleanup"))
        assertTrue(bridge.contains("DhApiBeforeTextureClearEvent"))
        assertTrue(bridge.contains("onBeforeTextureClear"))
        assertTrue(provider.contains("captureAvailableDepth"))
        assertTrue(provider.contains("fun captureCurrentFrame("))
        assertTrue(provider.contains("completedRenderState"))
        assertTrue(provider.contains("copy(frameToken = frameToken)"))
        assertTrue(provider.contains("capturedDepth"))
        assertTrue(provider.contains("GpuTexture.USAGE_COPY_SRC"))
        assertTrue(provider.contains("GpuTexture.USAGE_COPY_DST"))
        assertTrue(provider.contains("copyTextureToTexture"))
        assertTrue(provider.contains("GL43C.glCopyImageSubData"))
        assertTrue(provider.contains("captureOpenGl"))
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

    @Test
    fun `DH lifecycle owns its provider without depending on the parent engine package`() {
        val provider = read(DEPTH_PROVIDER)
        val capture = read(DEPTH_CAPTURE)
        val lifecycle = read(INTEGRATION_LIFECYCLE)

        assertTrue(provider.contains("package net.ccbluex.liquidbounce.render.engine.distanthorizons"))
        assertFalse(capture.contains("import net.ccbluex.liquidbounce.render.engine.DistantHorizonsDepthTextureProvider"))
        assertFalse(lifecycle.contains("import net.ccbluex.liquidbounce.render.engine.DistantHorizonsDepthTextureProvider"))
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private companion object {
        const val PUBLIC_EVENT_BRIDGE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsPublicEventBridge.kt"
        const val DEPTH_PROVIDER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/" +
                "DistantHorizonsDepthTextureProvider.kt"
        const val DEPTH_CAPTURE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsDepthCapture.kt"
        const val DEPTH_RESOURCES =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsDepthResources.kt"
        const val RENDER_API =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsRenderApi.kt"
        const val INTEGRATION_LIFECYCLE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/" +
                "DistantHorizonsIntegrationLifecycle.kt"
        const val MIXIN_CONFIG = "src/main/resources/liquidbounce.mixins.json"
    }
}
