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

package net.ccbluex.liquidbounce.render.engine.distanthorizons

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DistantHorizonsFogSuppressionContractTest {

    @Test
    fun `public DH fog event is canceled for Unified while Legacy keeps the volumetric policy`() {
        val bridge = Files.readString(Path.of(PUBLIC_EVENT_BRIDGE_PATH))
        val provider = Files.readString(Path.of(DEPTH_PROVIDER_PATH))
        val policy = Files.readString(Path.of(FOG_POLICY_PATH))
        val adapter = Files.readString(Path.of(FOG_ADAPTER_PATH))
        val installation = Files.readString(Path.of(FOG_INSTALLATION_PATH))
        val config = Files.readString(Path.of(MIXIN_CONFIG_PATH))

        assertTrue(bridge.contains("DhApiBeforeFogRenderEvent"))
        assertTrue(bridge.contains("sink.shouldSuppressNativeFog()"))
        assertTrue(bridge.contains("event.cancelEvent()"))
        assertTrue(provider.contains("override fun shouldSuppressNativeFog()"))
        assertTrue(provider.contains("DistantHorizonsFogPolicy.shouldSuppressNativeFog()"))
        assertTrue(policy.contains("fun install(policy: () -> Boolean)"))
        assertTrue(installation.contains("unified = FogValueGroup::isUnified"))
        assertTrue(adapter.contains("UnifiedFogRenderer::shouldReplaceNativeFog"))
        assertTrue(installation.contains("volumeRendering = { FogValueGroup.shouldRenderVolume }"))
        assertTrue(!config.contains("compat.distanthorizons.MixinDistantHorizonsFogRenderer"))
    }

    private companion object {
        const val PUBLIC_EVENT_BRIDGE_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsPublicEventBridge.kt"
        const val DEPTH_PROVIDER_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/" +
                "DistantHorizonsDepthTextureProvider.kt"
        const val FOG_POLICY_PATH =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/distanthorizons/DistantHorizonsFogPolicy.kt"
        const val FOG_ADAPTER_PATH = "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/" +
            "customambience/integration/DistantHorizonsFogAdapter.kt"
        const val FOG_INSTALLATION_PATH = "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/render/" +
            "customambience/ModuleCustomAmbience.kt"
        const val MIXIN_CONFIG_PATH = "src/main/resources/liquidbounce.mixins.json"
    }
}
