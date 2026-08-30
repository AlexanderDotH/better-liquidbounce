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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BrowserReadyEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.HudValueChangeEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.SpaceSeperatedNamesChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigUiBridge
import net.ccbluex.liquidbounce.features.misc.HideAppearance.isDestructed
import net.ccbluex.liquidbounce.features.misc.HideAppearance.isHidingNow
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud.themes
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudBlurEffectSettings
import net.ccbluex.liquidbounce.features.module.modules.render.hud.HudRuntimeBridge
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.gui.screens.Screen

/**
 * Module HUD
 *
 * The client in-game dashboard.
 */

enum class HudTheme(override val tag: String) : Tagged {
    CLASSIC("Classic"),
    MODERN("Modern"),
}

internal fun ValueGroup.hudThemeChoice() = enumChoice("Theme", HudTheme.MODERN).apply {
    val hud = this@hudThemeChoice
    onChanged {
        EventManager.callEvent(HudValueChangeEvent(hud))
    }
}

object ModuleHud : ClientModule("HUD", ModuleCategories.RENDER, state = true, hide = true), HudBlurEffectSettings {

    override val running
        get() = this.enabled && !isDestructed
    override val baseKey: String
        get() = "${ConfigSystem.KEY_PREFIX}.module.hud"

    private val isVisible: Boolean
        get() = !isHidingNow && inGame

    val theme by hudThemeChoice()

    var hudEditorSelected = false
        set(value) {
            if (value != field) {
                field = value
                updateOverlayVisibility(mc.gui.screen())
            }
        }

    private fun shouldShowOverlay(screen: Screen?): Boolean =
        screen !is DisconnectedScreen &&
            screen !is LevelLoadingScreen &&
            !(hudEditorSelected && isClickGuiScreen(screen))

    private fun isClickGuiScreen(screen: Screen?): Boolean =
        HudRuntimeBridge.isClickGuiScreen(screen)

    private fun updateOverlayVisibility(screen: Screen?) {
        if (!enabled || !isVisible) {
            overlay.close()
            return
        }

        overlay.visible = shouldShowOverlay(screen)
    }

    private var overlay = HudRuntimeBridge.createOverlay(::reopen)

    init {
        tree(Blur)
        AutoConfigUiBridge.installHudReopen(::reopen)
    }

    object Blur : ToggleableValueGroup(ModuleHud, "Blur", enabled = true) {
        /**
         * Gaussian sigma controlling blur strength. Higher values produce stronger blur.
         */
        val sigma by float("Sigma", 5.0F, 1.0F..15.0F)

        /**
         * The range in which the blending from not-blurred to blurred occurs.
         */
        val alphaBlendRange by floatRange("AlphaBlendRange", 0.0F..0.75F, 0.0F..1.0F)
    }

    @Suppress("unused")
    private val spaceSeperatedNames by boolean("SpaceSeperatedNames", true).onChange { state ->
        EventManager.callEvent(SpaceSeperatedNamesChangeEvent(state))
        state
    }

    val isBlurEffectActive
        get() = Blur.enabled && !(mc.gui.hud.isHidden && mc.gui.screen() == null)

    override fun enabled(): Boolean = running && isBlurEffectActive
    override fun sigma(): Float = Blur.sigma
    override fun alphaBlendStart(): Float = Blur.alphaBlendRange.start
    override fun alphaBlendEnd(): Float = Blur.alphaBlendRange.endInclusive

    val themes = tree(ValueGroup("Themes"))

    val components = tree(ValueGroup("AdditionalComponents")).apply {
        HudRuntimeBridge.additionalComponents().forEach(::tree)
    }

    /**
     * Updates [themes] content
     */
    fun updateThemes() {
        // filterIsInstance then forEach to prevent ConcurrentModificationException
        themes.inner.filterIsInstance<ValueGroup>().forEach {
            themes.drop(it)
        }
        HudRuntimeBridge.themeSettings().forEach(themes::tree)
        themes.walkInit()
        themes.walkKeyPath()
    }

    override fun onEnabled() {
        if (isHidingNow) {
            chat(markAsError(message("hidingAppearance")))
        }

        updateOverlayVisibility(mc.gui.screen())
    }

    override fun onDisabled() {
        overlay.close()
    }

    @Suppress("unused")
    private val browserReadyHandler = handler<BrowserReadyEvent> { event ->
        tree(overlay.browserSettings)
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent> { event ->
        updateOverlayVisibility(event.screen)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        overlay.close()
    }

    fun reopen() {
        overlay.close()
        updateOverlayVisibility(mc.gui.screen())
    }

}
