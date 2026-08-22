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

@file:Suppress("FunctionName", "PropertyName", "NOTHING_TO_INLINE", "CAST_NEVER_SUCCEEDS")

package net.ccbluex.liquidbounce.additions

import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

fun resolveServerboundPlayerInputSneak(rawSneak: Boolean, suppressSneak: Boolean, forceSneak: Boolean) =
    (rawSneak && !suppressSneak) || forceSneak

fun resolveServerboundPlayerInputJump(rawJump: Boolean, suppressJump: Boolean) = rawJump && !suppressJump

interface ServerboundPlayerInputPacketAddition {
    var `liquidBounce$forceSneak`: Boolean
    var `liquidBounce$suppressSneak`: Boolean
    var `liquidBounce$suppressJump`: Boolean
    var `liquidBounce$forceSprint`: Boolean

    fun `liquidBounce$getRawInput`(): Input
}

/**
 * Changes the return value of record component [ServerboundPlayerInputPacket.input].
 */
inline var ServerboundPlayerInputPacket.forceSneak: Boolean
    get() = (this as ServerboundPlayerInputPacketAddition).`liquidBounce$forceSneak`
    set(value) {
        (this as ServerboundPlayerInputPacketAddition).`liquidBounce$forceSneak` = value
    }

/**
 * Removes physical sneaking from this packet without changing the player's local input or pose.
 * An explicit [forceSneak] still takes precedence during packet serialization.
 */
inline var ServerboundPlayerInputPacket.suppressSneak: Boolean
    get() = (this as ServerboundPlayerInputPacketAddition).`liquidBounce$suppressSneak`
    set(value) {
        (this as ServerboundPlayerInputPacketAddition).`liquidBounce$suppressSneak` = value
    }

/** Removes physical jumping from this packet immediately before serialization. */
inline var ServerboundPlayerInputPacket.suppressJump: Boolean
    get() = (this as ServerboundPlayerInputPacketAddition).`liquidBounce$suppressJump`
    set(value) {
        (this as ServerboundPlayerInputPacketAddition).`liquidBounce$suppressJump` = value
    }

inline var ServerboundPlayerInputPacket.forceSprint: Boolean
    get() = (this as ServerboundPlayerInputPacketAddition).`liquidBounce$forceSprint`
    set(value) {
        (this as ServerboundPlayerInputPacketAddition).`liquidBounce$forceSprint` = value
    }

inline val ServerboundPlayerInputPacket.rawInput: Input
    get() = (this as ServerboundPlayerInputPacketAddition).`liquidBounce$getRawInput`()
