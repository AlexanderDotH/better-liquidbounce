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

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.BrowserReadyEvent
import net.ccbluex.liquidbounce.event.events.ClickGuiScaleChangeEvent
import net.ccbluex.liquidbounce.event.events.ClickGuiValueChangeEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitSeconds
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigUiBridge
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.CachedClickGuiScreenBridge
import net.ccbluex.liquidbounce.features.module.modules.render.clickgui.ClickGuiRuntimeBridge
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE

/**
 * ClickGUI module
 *
 * Shows you an easy-to-use menu to toggle and configure modules.
 */

enum class ClickGuiTheme(override val tag: String) : Tagged {
    CLASSIC("Classic"),
    MODERN("Modern"),
}

object ModuleClickGui :
    ClientModule("ClickGUI", ModuleCategories.RENDER, bind = InputConstants.KEY_RSHIFT, disableActivation = true) {

    override val running get() = true

    val theme by enumChoice("Theme", ClickGuiTheme.MODERN).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty")
    private val scale by float("Scale", 1f, 0.5f..2f).onChanged {
        EventManager.callEvent(ClickGuiScaleChangeEvent(it))
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    @Suppress("UnusedPrivateProperty", "unused")
    private val searchBarAutoFocus by boolean("SearchBarAutoFocus", true).onChanged {
        EventManager.callEvent(ClickGuiValueChangeEvent(this))
    }

    val isInSearchBar: Boolean
        get() {
            if (!ClickGuiRuntimeBridge.isTyping()) {
                return false
            }

            return ClickGuiRuntimeBridge.isClickGuiScreen(mc.gui.screen())
        }

    object Snapping : ToggleableValueGroup(this, "Snapping", true) {

        @Suppress("UnusedPrivateProperty", "unused")
        private val gridSize by int("GridSize", 10, 1..100, "px").onChanged {
            EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
        }

        init {
            inner.find { it.name == "Enabled" }?.onChanged {
                EventManager.callEvent(ClickGuiValueChangeEvent(ModuleClickGui))
            }
        }
    }

    init {
        tree(Snapping)
        AutoConfigUiBridge.installClickGuiSync(::sync)
    }

    @Suppress("UnusedPrivateProperty")
    private val useStandaloneScreen by boolean("Cache", true).onChanged {
        mc.execute(::onEnabled)
    }

    // Standalone screen instance for caching
    private var standaloneScreen: CachedClickGuiScreenBridge? = null

    @Suppress("unused")
    private val browserReadyHandler = handler<BrowserReadyEvent>(priority = READ_FINAL_STATE) {
        tree(ClickGuiRuntimeBridge.browserSettings())
    }

    override fun onEnabled() {
        if (!ClickGuiRuntimeBridge.isClientInitialized() || !inGame) {
            return
        }

        updateStandaloneScreen()
        mc.execute {
            mc.gui.setScreen(standaloneScreen?.screen ?: ClickGuiRuntimeBridge.createSharedScreen())
        }
        super.onEnabled()
    }

    @Suppress("unused")
    private val worldChangeHandler = sequenceHandler<WorldChangeEvent>(
        priority = OBJECTION_AGAINST_EVERYTHING
    ) { event ->
        if (event.world == null || !useStandaloneScreen) {
            return@sequenceHandler
        }

        waitSeconds(1)
        if (updateStandaloneScreen()) {
            standaloneScreen?.sync()
        }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        standaloneScreen?.close()
        standaloneScreen = null
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        // For some reason, we actually need this.
        standaloneScreen?.browserVisible = mc.gui.screen() == standaloneScreen?.screen
    }

    fun updateStandaloneScreen(): Boolean {
        // Standalone Screen Cache
        if (useStandaloneScreen) {
            if (standaloneScreen == null) {
                standaloneScreen = ClickGuiRuntimeBridge.createStandaloneScreen()
            } else {
                // Used in [worldChangeHandler] to determine if we need to sync.
                return true
            }
        } else if (standaloneScreen != null) {
            standaloneScreen?.close()
            standaloneScreen = null
        }

        return false
    }

    fun sync() {
        if (!ClickGuiRuntimeBridge.isClientInitialized()) {
            return
        }

        standaloneScreen?.sync()
    }

    fun invalidate() {
        val standaloneScreen = standaloneScreen ?: return
        val wasOpen = mc.gui.screen() == standaloneScreen.screen

        // Close and invalidate old cache
        if (wasOpen) {
            mc.gui.setScreen(null)
        }
        standaloneScreen.close()
        this.standaloneScreen = null

        // Only bother updating now if it was open before.
        if (wasOpen) {
            updateStandaloneScreen()
            mc.gui.setScreen(this.standaloneScreen?.screen ?: ClickGuiRuntimeBridge.createSharedScreen())
        }
    }

}
