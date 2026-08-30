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
package net.ccbluex.liquidbounce.features.module

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.utils.client.clientStartDurationMs
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.input.InputBind

internal class ModuleBindController(
    private val modules: Collection<ClientModule>,
) {
    private enum class SmartKeyboardState {
        PENDING_ENABLED, PENDING_DISABLED, HOLDING,
    }

    private data class SmartMouseState(val pendingEnabled: Boolean, val pressTimestamp: Long)

    private val keyboardStates = Reference2ObjectArrayMap<ClientModule, SmartKeyboardState>()
    private val mouseStates = Reference2ObjectArrayMap<ClientModule, SmartMouseState>()

    fun handleKeyboard(event: KeyboardKeyEvent) {
        when {
            event.isPressed -> handleKeyboardPress(event)
            event.isRepeat -> handleKeyboardRepeat(event)
            event.isReleased -> handleKeyboardRelease(event)
        }
    }

    private fun handleKeyboardPress(event: KeyboardKeyEvent) {
        if (mc.gui.screen() != null || mc.options.keyDebugModifier.isDown) return
        modules.filter { it.bind.matchesKeyPress(event) }.forEach { module ->
            when (module.bind.action) {
                InputBind.BindAction.TOGGLE -> module.enabled = !module.enabled
                InputBind.BindAction.HOLD -> module.enabled = true
                InputBind.BindAction.SMART -> {
                    keyboardStates[module] = if (module.enabled) {
                        SmartKeyboardState.PENDING_ENABLED
                    } else {
                        SmartKeyboardState.PENDING_DISABLED
                    }
                    module.enabled = true
                }
            }
        }
    }

    private fun handleKeyboardRepeat(event: KeyboardKeyEvent) {
        modules.filter { module ->
            module.bind.action == InputBind.BindAction.SMART &&
                module.bind.matchesKey(event.keyCode, event.scanCode) &&
                module in keyboardStates
        }.forEach { keyboardStates[it] = SmartKeyboardState.HOLDING }
    }

    private fun handleKeyboardRelease(event: KeyboardKeyEvent) {
        modules.filter { it.bind.matchesKeyRelease(event) }.forEach { module ->
            when (module.bind.action) {
                InputBind.BindAction.HOLD -> module.enabled = false
                InputBind.BindAction.SMART -> {
                    val previous = keyboardStates.remove(module) ?: return@forEach
                    module.enabled = previous == SmartKeyboardState.PENDING_DISABLED
                }
                InputBind.BindAction.TOGGLE -> Unit
            }
        }
    }

    fun handleMouse(event: MouseButtonEvent) {
        when {
            event.isPressed -> handleMousePress(event)
            event.isReleased -> handleMouseRelease(event)
        }
    }

    private fun handleMousePress(event: MouseButtonEvent) {
        if (mc.gui.screen() != null) return
        modules.filter { it.bind.matchesMousePress(event) }.forEach { module ->
            when (module.bind.action) {
                InputBind.BindAction.TOGGLE -> module.enabled = !module.enabled
                InputBind.BindAction.HOLD -> module.enabled = true
                InputBind.BindAction.SMART -> {
                    mouseStates[module] = SmartMouseState(module.enabled, clientStartDurationMs)
                    module.enabled = true
                }
            }
        }
    }

    private fun handleMouseRelease(event: MouseButtonEvent) {
        modules.filter { it.bind.matchesMouseRelease(event) }.forEach { module ->
            when (module.bind.action) {
                InputBind.BindAction.HOLD -> module.enabled = false
                InputBind.BindAction.SMART -> finishSmartMouseBind(module)
                InputBind.BindAction.TOGGLE -> Unit
            }
        }
    }

    private fun finishSmartMouseBind(module: ClientModule) {
        val state = mouseStates.remove(module) ?: return
        val heldLongEnough = clientStartDurationMs - state.pressTimestamp >= SMART_MOUSE_HOLD_THRESHOLD_MS
        module.enabled = if (heldLongEnough) false else !state.pendingEnabled
    }

    private companion object {
        const val SMART_MOUSE_HOLD_THRESHOLD_MS = 200L
    }
}
