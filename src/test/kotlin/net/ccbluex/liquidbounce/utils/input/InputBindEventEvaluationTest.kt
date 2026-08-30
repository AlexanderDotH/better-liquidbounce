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
import java.lang.reflect.Modifier as JavaModifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InputBindEventEvaluationTest {

    @Test
    fun `keyboard mouse and modifier matching retain their established semantics`() {
        val keyboardBind = InputBind(
            InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_A),
            InputBind.BindAction.TOGGLE,
            setOf(InputBind.Modifier.SHIFT, InputBind.Modifier.CONTROL),
        )
        val mouseBind = InputBind(
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_LEFT,
            InputBind.BindAction.HOLD,
        )
        val modifiers = InputConstants.MOD_SHIFT or InputConstants.MOD_CONTROL

        assertTrue(keyboardBind.matchesKey(InputConstants.KEY_A, scanCode = 0))
        assertFalse(keyboardBind.matchesKey(InputConstants.KEY_C, scanCode = 0))
        assertTrue(keyboardBind.matchesModifiers(modifiers))
        assertFalse(keyboardBind.matchesModifiers(InputConstants.MOD_SHIFT))
        assertTrue(keyboardBind.matchesKeyPress(keyState(InputConstants.KEY_A, modifiers, pressed = true)))
        assertFalse(keyboardBind.matchesKeyPress(keyState(InputConstants.KEY_A, modifiers, pressed = false)))
        assertTrue(mouseBind.matchesMouse(InputConstants.MOUSE_BUTTON_LEFT))
        assertFalse(mouseBind.matchesMouse(InputConstants.MOUSE_BUTTON_RIGHT))
        assertTrue(mouseBind.matchesMousePress(mouseState(InputConstants.MOUSE_BUTTON_LEFT, pressed = true)))
        assertFalse(mouseBind.matchesMousePress(mouseState(InputConstants.MOUSE_BUTTON_LEFT, pressed = false)))
    }

    @Test
    fun `scancode and release paths retain their state transitions without a client window`() {
        val scanCode = 17
        val scanBind = InputBind(InputConstants.Type.SCANCODE, scanCode, InputBind.BindAction.TOGGLE)
        val release = keyState(InputConstants.KEY_A, released = true)

        assertTrue(scanBind.matchesKey(InputConstants.UNKNOWN.value, scanCode))
        assertFalse(scanBind.matchesKey(InputConstants.UNKNOWN.value, scanCode + 1))
        assertTrue(bind(InputBind.BindAction.HOLD).matchesKeyRelease(release))
        assertTrue(mouseBind().matchesMouseRelease(mouseState(InputConstants.MOUSE_BUTTON_LEFT, released = true)))
        assertFalse(bind(InputBind.BindAction.HOLD).getNewState(release, currentState = true))
        assertTrue(bind(InputBind.BindAction.TOGGLE).getNewState(release, currentState = true))
        assertTrue(bind(InputBind.BindAction.SMART).getNewState(release, currentState = true))
        assertTrue(
            bind(InputBind.BindAction.HOLD).getNewState(
                keyState(InputConstants.KEY_C, pressed = true),
                currentState = true,
            )
        )
    }

    @Test
    fun `record nested enums lookups and string forms retain their JVM contract`() {
        val inputBindClass = InputBind::class.java
        val actionClass = InputBind.BindAction::class.java
        val modifierClass = InputBind.Modifier::class.java

        assertTrue(inputBindClass.isRecord)
        assertEquals(listOf("boundKey", "action", "modifiers"), inputBindClass.recordComponents.map { it.name })
        assertEquals("${inputBindClass.name}\$BindAction", actionClass.name)
        assertEquals("${inputBindClass.name}\$Modifier", modifierClass.name)
        assertTrue(JavaModifier.isStatic(actionClass.getMethod("of", String::class.java).modifiers))
        assertTrue(JavaModifier.isStatic(modifierClass.getMethod("of", String::class.java).modifiers))
        assertTrue(JavaModifier.isStatic(modifierClass.getMethod("of", Integer.TYPE).modifiers))
        assertTrue(JavaModifier.isStatic(modifierClass.getMethod("fromRawValue", Integer.TYPE).modifiers))
        assertSame(InputBind.UNBOUND, inputBindClass.getField("UNBOUND").get(null))
        assertEquals(InputBind.BindAction.HOLD, InputBind.BindAction.of("Hold"))
        assertEquals(InputBind.Modifier.SHIFT, InputBind.Modifier.of("Shift"))
        assertEquals(InputBind.Modifier.SHIFT, InputBind.Modifier.of(InputConstants.KEY_LSHIFT))
        assertEquals(
            listOf(InputBind.Modifier.SHIFT, InputBind.Modifier.ALT),
            InputBind.Modifier.fromRawValue(InputConstants.MOD_SHIFT or InputConstants.MOD_ALT).toList(),
        )
        assertEquals("A", bind(InputBind.BindAction.TOGGLE).keyName)
        assertEquals("None", InputBind.UNBOUND.keyName)
    }

    @Test
    fun `public facade delegates event evaluation to one same-package responsibility`() {
        val facade = Files.readString(INPUT_BIND_SOURCE)
        assertTrue(Files.exists(EVENT_EVALUATOR_SOURCE))
        val evaluator = Files.readString(EVENT_EVALUATOR_SOURCE)

        listOf(
            "BindEventEvaluator.matchesKey(this, keyCode, scanCode)",
            "BindEventEvaluator.matchesMouse(this, code)",
            "BindEventEvaluator.matchesModifiers(this, mods)",
            "BindEventEvaluator.matchesKeyPress(this, event)",
            "BindEventEvaluator.matchesKeyRelease(this, event)",
            "BindEventEvaluator.matchesMousePress(this, event)",
            "BindEventEvaluator.matchesMouseRelease(this, event)",
            "BindEventEvaluator.getNewState(this, event, currentState)",
            "BindEventEvaluator.isAnyModifierKeyPressed(this)",
        ).forEach { delegate -> assertTrue(delegate in facade, "Missing InputBind facade delegate: $delegate") }
        assertTrue("internal object BindEventEvaluator" in evaluator)
        assertFalse("mc.gui.screen()" in facade)
        assertTrue("mc.gui.screen()" in evaluator)
    }

    private fun bind(action: InputBind.BindAction) = InputBind(InputConstants.Type.KEYSYM, InputConstants.KEY_A, action)

    private fun mouseBind() = InputBind(
        InputConstants.Type.MOUSE,
        InputConstants.MOUSE_BUTTON_LEFT,
        InputBind.BindAction.HOLD,
    )

    private fun keyState(
        keyCode: Int,
        mods: Int = 0,
        pressed: Boolean = false,
        released: Boolean = false,
    ): KeyboardInputState = object : KeyboardInputState {
        override val key = InputConstants.Type.KEYSYM.getOrCreate(keyCode)
        override val keyCode = keyCode
        override val scanCode = 0
        override val mods = mods
        override val isPressed = pressed
        override val isReleased = released
    }

    private fun mouseState(
        button: Int,
        pressed: Boolean = false,
        released: Boolean = false,
    ): MouseInputState = object : MouseInputState {
        override val key = InputConstants.Type.MOUSE.getOrCreate(button)
        override val button = button
        override val mods = 0
        override val isPressed = pressed
        override val isReleased = released
    }

    private companion object {
        val INPUT_BIND_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/input/InputBind.kt"
        )
        val EVENT_EVALUATOR_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/input/BindEventEvaluator.kt"
        )
    }
}
