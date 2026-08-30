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
package net.ccbluex.liquidbounce.utils.input

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.common.input.KeyboardInputState
import net.ccbluex.liquidbounce.common.input.MouseInputState
import net.ccbluex.liquidbounce.utils.client.mc

/** Evaluates low-level keyboard and mouse state against an [InputBind]. */
internal object BindEventEvaluator {

    fun matchesKey(bind: InputBind, keyCode: Int, scanCode: Int): Boolean {
        return if (keyCode == InputConstants.UNKNOWN.value) {
            bind.boundKey.type == InputConstants.Type.SCANCODE && bind.boundKey.value == scanCode
        } else {
            bind.boundKey.type == InputConstants.Type.KEYSYM && bind.boundKey.value == keyCode
        }
    }

    fun matchesMouse(bind: InputBind, code: Int): Boolean {
        return bind.boundKey.type == InputConstants.Type.MOUSE && bind.boundKey.value == code
    }

    fun matchesModifiers(bind: InputBind, modifiers: Int): Boolean {
        return bind.modifiers.all { it.isActive(modifiers) }
    }

    fun matchesKeyPress(bind: InputBind, event: KeyboardInputState): Boolean {
        return event.isPressed
            && matchesKey(bind, event.keyCode, event.scanCode)
            && matchesModifiers(bind, event.mods)
    }

    fun matchesKeyRelease(bind: InputBind, event: KeyboardInputState): Boolean {
        if (!event.isReleased) return false
        val keyReleased = matchesKey(bind, event.keyCode, event.scanCode)
        val modifierReleased = event.key.toModifierOrNull().let {
            it in bind.modifiers && !it!!.isAnyPressed
        }

        return keyReleased || modifierReleased
    }

    fun matchesMousePress(bind: InputBind, event: MouseInputState): Boolean {
        return event.isPressed
            && matchesMouse(bind, event.button)
            && matchesModifiers(bind, event.mods)
    }

    fun matchesMouseRelease(bind: InputBind, event: MouseInputState): Boolean {
        if (!event.isReleased) return false
        val buttonReleased = matchesMouse(bind, event.button)
        val modifierReleased = event.key.toModifierOrNull().let {
            it in bind.modifiers && !it!!.isAnyPressed
        }

        return buttonReleased || modifierReleased
    }

    fun getNewState(bind: InputBind, event: KeyboardInputState, currentState: Boolean): Boolean {
        if (!matchesKey(bind, event.keyCode, event.scanCode)) {
            return currentState
        }

        return when {
            event.isPressed && mc.gui.screen() == null -> when (bind.action) {
                InputBind.BindAction.TOGGLE -> !currentState
                InputBind.BindAction.HOLD, InputBind.BindAction.SMART -> true
            }
            event.isReleased -> when (bind.action) {
                InputBind.BindAction.HOLD -> false
                InputBind.BindAction.TOGGLE, InputBind.BindAction.SMART -> currentState
            }
            else -> currentState
        }
    }

    fun isAnyModifierKeyPressed(modifier: InputBind.Modifier): Boolean {
        return modifier.keyCodes.any { InputConstants.isKeyDown(mc.window, it) }
    }
}
