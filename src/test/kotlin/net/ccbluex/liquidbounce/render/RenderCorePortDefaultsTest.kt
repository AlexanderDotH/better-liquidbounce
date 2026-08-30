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
package net.ccbluex.liquidbounce.render

import net.ccbluex.liquidbounce.common.EspMaskRequest
import net.ccbluex.liquidbounce.render.engine.BlurEffectPolicy
import net.ccbluex.liquidbounce.render.engine.CustomFogInteractionBridge
import net.ccbluex.liquidbounce.render.engine.CustomFogRenderBridge
import net.ccbluex.liquidbounce.render.engine.esp.EspMaskFeatureSelectorRegistry
import net.ccbluex.liquidbounce.render.engine.font.ForeignTextSanitizer
import net.ccbluex.liquidbounce.render.utils.RenderDebugSink
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RenderCorePortDefaultsTest {

    @Test
    fun `uninstalled feature ports fail closed without changing text`() {
        CustomFogRenderBridge.withAdapterForTest(null) {
            assertFalse(CustomFogRenderBridge.activity().customAmbienceRunning)
            assertFalse(CustomFogRenderBridge.activity().shouldRenderUnified)
        }
        BlurEffectPolicy.withAdapterForTest(null) {
            assertFalse(BlurEffectPolicy.shouldRenderHudBlur())
            assertFalse(BlurEffectPolicy.shouldHideScreen())
            assertSame(BlurEffectPolicy.disabledState, BlurEffectPolicy.state())
        }
        val component = Component.literal("unchanged")
        ForeignTextSanitizer.withSanitizerForTest(null) {
            assertSame(component, ForeignTextSanitizer.sanitize(component))
        }
    }

    @Test
    fun `uninstalled ESP selector contributes no feature masks`() {
        EspMaskFeatureSelectorRegistry.withSelectorForTest(null) {
            assertSame(EspMaskRequest.NONE, EspMaskFeatureSelectorRegistry.forEntity(null))
            assertSame(EspMaskRequest.NONE, EspMaskFeatureSelectorRegistry.forBlockEntity(null))
        }
    }

    @Test
    fun `uninstalled debug sink is a safe no-op`() {
        RenderDebugSink.withSinkForTest(null) {
            assertDoesNotThrow { RenderDebugSink.publishRenderPassCount(7) }
        }
    }

    @Test
    fun `uninstalled fog interaction port is inactive and delegates an installed provider`() {
        CustomFogInteractionBridge.withProviderForTest(null) {
            assertFalse(CustomFogInteractionBridge.active())
        }
        CustomFogInteractionBridge.withProviderForTest({ true }) {
            assert(CustomFogInteractionBridge.active())
        }
    }

    @Test
    fun `render setup factory reports missing injection adapter`() {
        RenderInjectionAccess.withRenderSetupFactoryForTest(null) {
            val failure = assertThrows(IllegalStateException::class.java) {
                RenderInjectionAccess.copyWithOutputTarget(null, null)
            }
            assert(failure.message.orEmpty().contains("RenderSetup injection adapter"))
        }
    }

    @Test
    fun `HUD selection sprite bridge fails fast and delegates an installed provider`() {
        HudSelectionSpriteBridge.withProviderForTest(null) {
            assertThrows(IllegalStateException::class.java, HudSelectionSpriteBridge::texture)
        }
        val expected = Identifier.fromNamespaceAndPath("liquidbounce", "test_selection")
        HudSelectionSpriteBridge.withProviderForTest({ expected }) {
            assertSame(expected, HudSelectionSpriteBridge.texture())
        }
    }
}
