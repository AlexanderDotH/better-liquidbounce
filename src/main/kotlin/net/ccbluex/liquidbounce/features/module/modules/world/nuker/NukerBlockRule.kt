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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

internal enum class NukerBlockRule(override val tag: String) : Tagged {
    ALL("All") {
        override fun accepts(candidate: Block, selectedBlock: Block?) = true
    },
    SAME_BLOCK("SameBlock") {
        override fun accepts(candidate: Block, selectedBlock: Block?) = candidate == selectedBlock
    };

    abstract fun accepts(candidate: Block, selectedBlock: Block?): Boolean
}

internal fun shouldNukerBreakImmediately(forceImmediateBreak: Boolean, fastBreakRunning: Boolean): Boolean {
    return forceImmediateBreak && !fastBreakRunning
}

internal fun isManualNukerSelection(
    attackKeyDown: Boolean,
    crosshairPos: BlockPos?,
    minedPos: BlockPos,
): Boolean {
    return attackKeyDown && crosshairPos == minedPos
}

/**
 * Gives physical mouse input a short grace period in which Nuker must not renew its rotation target.
 * Repeated input refreshes the grace period so the player always wins an active rotation tug-of-war.
 */
internal class NukerPlayerInputOverride(
    private val durationTicks: Int = DEFAULT_DURATION_TICKS,
) {

    private var remainingTicks = 0

    val active: Boolean
        get() = remainingTicks > 0

    init {
        require(durationTicks > 0) { "durationTicks must be positive" }
    }

    fun activate() {
        remainingTicks = durationTicks
    }

    fun tick() {
        remainingTicks = (remainingTicks - 1).coerceAtLeast(0)
    }

    fun reset() {
        remainingTicks = 0
    }

    private companion object {
        const val DEFAULT_DURATION_TICKS = 8
    }
}
