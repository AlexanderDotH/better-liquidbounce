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
package net.ccbluex.liquidbounce.features.module.modules.misc

internal enum class MiddleClickSmartTarget {
    PLAYER,
    BLOCK,
    AIR,
}

internal data class MiddleClickSmartOptions(
    val friendClicker: Boolean,
    val pearl: Boolean,
    val amnesiaTarget: Boolean,
    val nukerBlock: Boolean,
    val vClipLock: Boolean,
)

@Suppress("LongParameterList")
internal data class MiddleClickSmartInput(
    val target: MiddleClickSmartTarget,
    val options: MiddleClickSmartOptions,
    val friendTargetAcquired: Boolean = false,
    val amnesiaRunning: Boolean = false,
    val amnesiaTargetAcquired: Boolean = false,
    val nukerRunning: Boolean = false,
    val vClipRunning: Boolean = false,
)

internal enum class MiddleClickSmartAction {
    FRIEND_CLICKER,
    PEARL,
    AMNESIA_TARGET,
    NUKER_BLOCK,
    VCLIP_HOLD,
    NONE,
}

internal object MiddleClickSmartResolver {

    fun resolve(input: MiddleClickSmartInput): MiddleClickSmartAction = when (input.target) {
        MiddleClickSmartTarget.PLAYER -> resolvePlayer(input)
        MiddleClickSmartTarget.BLOCK -> resolveBlock(input)
        MiddleClickSmartTarget.AIR -> resolveAir(input)
    }

    private fun resolvePlayer(input: MiddleClickSmartInput): MiddleClickSmartAction {
        val canSelectAmnesia = input.options.amnesiaTarget &&
            input.amnesiaRunning && input.amnesiaTargetAcquired
        if (canSelectAmnesia) return MiddleClickSmartAction.AMNESIA_TARGET
        if (input.options.friendClicker && input.friendTargetAcquired) {
            return MiddleClickSmartAction.FRIEND_CLICKER
        }

        return MiddleClickSmartAction.NONE
    }

    private fun resolveBlock(input: MiddleClickSmartInput): MiddleClickSmartAction {
        if (input.options.nukerBlock && input.nukerRunning) {
            return MiddleClickSmartAction.NUKER_BLOCK
        }

        return resolveVClipHold(input)
    }

    private fun resolveAir(input: MiddleClickSmartInput): MiddleClickSmartAction {
        val vClipAction = resolveVClipHold(input)
        if (vClipAction != MiddleClickSmartAction.NONE) return vClipAction
        if (input.options.pearl) return MiddleClickSmartAction.PEARL

        return MiddleClickSmartAction.NONE
    }

    private fun resolveVClipHold(input: MiddleClickSmartInput): MiddleClickSmartAction {
        return if (input.options.vClipLock && input.vClipRunning) {
            MiddleClickSmartAction.VCLIP_HOLD
        } else {
            MiddleClickSmartAction.NONE
        }
    }
}
