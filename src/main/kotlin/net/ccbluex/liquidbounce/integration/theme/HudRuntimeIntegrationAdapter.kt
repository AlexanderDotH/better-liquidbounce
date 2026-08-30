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
package net.ccbluex.liquidbounce.integration.theme

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudOverlayHandle
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudRuntimeProvider
import net.ccbluex.liquidbounce.integration.backend.browser.BrowserSettings
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.screen.impl.CustomOverlay
import net.ccbluex.liquidbounce.integration.screen.impl.CustomSharedMinecraftScreen
import net.ccbluex.liquidbounce.integration.screen.impl.CustomStandaloneMinecraftScreen
import net.ccbluex.liquidbounce.integration.theme.component.components.minimap.MinimapHudComponent
import net.ccbluex.liquidbounce.integration.theme.component.components.seedcracker.SeedCrackerHudComponent
import net.minecraft.client.gui.screens.Screen

object HudRuntimeIntegrationAdapter {

    @JvmStatic
    fun install() = HudRuntimeBridge.install(Provider)

    private object Provider : HudRuntimeProvider {
        override fun createOverlay(reopen: () -> Unit): HudOverlayHandle = HudOverlay(
            CustomOverlay(CustomScreenType.HUD, BrowserSettings(60, Runnable(reopen)))
        )

        override fun isClickGuiScreen(screen: Screen?): Boolean =
            screen is CustomSharedMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI ||
                screen is CustomStandaloneMinecraftScreen && screen.screenType == CustomScreenType.CLICK_GUI

        override fun themeSettings(): List<ValueGroup> = ThemeManager.themes.map { it.settings }

        override fun additionalComponents(): List<ValueGroup> =
            listOf(MinimapHudComponent, SeedCrackerHudComponent)
    }
}

private class HudOverlay(private val overlay: CustomOverlay) : HudOverlayHandle {
    override val browserSettings: ValueGroup get() = overlay.browserSettings
    override var visible: Boolean
        get() = overlay.visible
        set(value) { overlay.visible = value }

    override fun close() = overlay.close()
}
