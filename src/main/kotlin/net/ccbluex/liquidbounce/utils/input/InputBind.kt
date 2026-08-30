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
@file:JvmName("InputBindKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.input

import com.mojang.blaze3d.platform.InputConstants
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.fastutil.unmodifiable
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.common.Tagged.Companion.makeLookupTable
import net.ccbluex.liquidbounce.common.input.KeyboardInputState
import net.ccbluex.liquidbounce.common.input.MouseInputState
import net.minecraft.util.Util

/**
 * Data class representing a key binding.
 * It holds the key to be bound and the action that will be triggered by the binding.
 *
 * @param boundKey The key that is bound to an action.
 * @param action The action triggered by the bound key (e.g., TOGGLE, HOLD).
 */
@JvmRecord
data class InputBind(
    val boundKey: InputConstants.Key,
    val action: BindAction,
    val modifiers: Set<Modifier>,
) {

    /**
     * Alternative constructor to create a binding from the key type and key code.
     *
     * @param type The type of input (keyboard, mouse, etc.).
     * @param code The key or button code.
     * @param action The action to bind to this key.
     */
    constructor(type: InputConstants.Type, code: Int, action: BindAction) :
        this(type.getOrCreate(code), action, emptySet())

    /**
     * Constructor to create a binding using a key name.
     *
     * @param name The name of the key, which will be translated to an InputUtil.Key.
     */
    constructor(name: String) :
        this(inputByName(name), BindAction.TOGGLE, emptySet())

    /**
     * Retrieves the name of the key in uppercase format, excluding the category prefixes.
     *
     * @return A formatted string representing the bound key's name, or "None" if unbound.
     */
    val keyName: String
        get() = when {
            isUnbound -> "None"
            else -> this.boundKey.name
                .split('.')
                .drop(2) // Drops the "key.keyboard" or "key.mouse" part
                .joinToString(separator = "_") // Joins the remaining parts with underscores
                .uppercase() // Converts the key name to uppercase
        }

    /**
     * Checks if the key is unbound (i.e., set to UNKNOWN_KEY).
     *
     * @return True if the key is unbound, false otherwise.
     */
    val isUnbound: Boolean
        get() = this.boundKey == InputConstants.UNKNOWN

    /**
     * Determines if the specified key matches the bound key.
     *
     * @param keyCode The InputConstants key code to check.
     * @param scanCode The scan code to check.
     * @return True if the key code or scan code matches the bound key, false otherwise.
     */
    fun matchesKey(keyCode: Int, scanCode: Int): Boolean = BindEventEvaluator.matchesKey(this, keyCode, scanCode)

    /**
     * Determines if the specified mouse button code matches the bound key.
     *
     * @param code The mouse button code to check.
     * @return True if the mouse button matches the bound key, false otherwise.
     */
    fun matchesMouse(code: Int): Boolean = BindEventEvaluator.matchesMouse(this, code)

    /**
     * Determines if the given modifiers match the required modifiers.
     *
     * @param mods The bits of modifiers.
     * @see InputConstants
     */
    fun matchesModifiers(mods: Int): Boolean = BindEventEvaluator.matchesModifiers(this, mods)

    /**
     * Determines if a keyboard press event matches this bind key and required modifiers.
     */
    fun matchesKeyPress(event: KeyboardInputState): Boolean = BindEventEvaluator.matchesKeyPress(this, event)

    /**
     * Determines if a keyboard release affects this bind key or one of its required modifiers.
     */
    fun matchesKeyRelease(event: KeyboardInputState): Boolean = BindEventEvaluator.matchesKeyRelease(this, event)

    /**
     * Determines if a mouse press event matches this bind button and required modifiers.
     */
    fun matchesMousePress(event: MouseInputState): Boolean = BindEventEvaluator.matchesMousePress(this, event)

    /**
     * Determines if a mouse release affects this bind button or one of its required modifiers.
     */
    fun matchesMouseRelease(event: MouseInputState): Boolean = BindEventEvaluator.matchesMouseRelease(this, event)

    /**
     * Handles the event. Returns the new state, assumes the original state is `false`.
     *
     * @param event The [KeyboardInputState] to handle.
     * @param currentState The current state.
     * @return The new state.
     */
    fun getNewState(event: KeyboardInputState, currentState: Boolean): Boolean =
        BindEventEvaluator.getNewState(this, event, currentState)

    /**
     * Action mode used to interpret bind input events.
     *
     * @param tag display name used in config/ui
     */
    enum class BindAction(override val tag: String) : Tagged {
        /**
         * Flip state when pressed.
         */
        TOGGLE("Toggle"),

        /**
         * Stay enabled while key is held and disable on release.
         */
        HOLD("Hold"),

        /**
         * Start as enabled on press, then classify as:
         * - hold if a repeat event is received before release
         * - toggle if release is received first
         * - toggle on unexpected fallback paths
         */
        SMART("Smart");

        companion object {
            @JvmStatic
            private val LOOKUP_TABLE = BindAction.entries.makeLookupTable()

            @JvmStatic
            fun of(string: String?): BindAction? = LOOKUP_TABLE[string]
        }
    }

    enum class Modifier(override val tag: String, val bitMask: Int, vararg val keyCodes: Int): Tagged {
        SHIFT("Shift", InputConstants.MOD_SHIFT, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT),
        CONTROL("Control", InputConstants.MOD_CONTROL, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL),
        ALT("Alt", InputConstants.MOD_ALT, InputConstants.KEY_LALT, InputConstants.KEY_RALT),
        SUPER("Super", InputConstants.MOD_SUPER, InputConstants.KEY_LSUPER, InputConstants.KEY_RSUPER);

        /**
         * Check if self is active in [modifiers] value.
         */
        fun isActive(modifiers: Int) = modifiers and this.bitMask != 0

        /**
         * Check if any one modifier key is pressed.
         */
        val isAnyPressed: Boolean get() = BindEventEvaluator.isAnyModifierKeyPressed(this)

        /**
         * Performs the platform (OS) specified render name of a modifier.
         */
        val platformRenderName: String get() = when (Util.getPlatform()) {
            Util.OS.WINDOWS -> when (this) {
                CONTROL -> "Ctrl"
                SUPER -> "\u229e"
                else -> tag
            }
            Util.OS.OSX -> when (this) {
                SHIFT -> "\u21e7"
                CONTROL -> "^"
                ALT -> "\u2325"
                SUPER -> "\u2318"
                // else -> choiceName
            }
            else -> tag
        }

        companion object {
            @JvmStatic
            private val LOOKUP_TABLE = Modifier.entries.makeLookupTable()

            @JvmStatic
            private val KEY_CODE_LOOKUP: Int2ReferenceMap<Modifier> = run {
                val map = Int2ReferenceOpenHashMap<Modifier>()
                for (modifier in Modifier.entries) {
                    for (keyCode in modifier.keyCodes) {
                        map.put(keyCode, modifier)
                    }
                }
                map.unmodifiable()
            }

            @JvmStatic
            fun of(string: String?): Modifier? = LOOKUP_TABLE[string]

            @JvmStatic
            fun of(keyCode: Int): Modifier? = KEY_CODE_LOOKUP[keyCode]

            @JvmStatic
            fun fromRawValue(modifiers: Int) = entries.filterTo(enumSetOf()) {
                it.isActive(modifiers)
            }
        }
    }

    companion object {
        @JvmField
        val UNBOUND = InputBind(InputConstants.UNKNOWN, BindAction.TOGGLE, emptySet())
    }

}
