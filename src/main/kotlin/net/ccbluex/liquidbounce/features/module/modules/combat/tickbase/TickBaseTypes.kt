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
package net.ccbluex.liquidbounce.features.module.modules.combat.tickbase

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.phys.Vec3

@JvmRecord
internal data class TickData(
    val position: Vec3,
    val fallDistance: Double,
    val velocity: Vec3,
    val onGround: Boolean,
)

internal enum class TickBaseMode(override val tag: String) : Tagged {
    PAST("Past"),
    FUTURE("Future"),
}

internal enum class TickBaseCall(
    override val tag: String,
    private val tick: Runnable,
) : Tagged {
    /** Runs a full game tick. */
    GAME("Game", { mc.tick() }),

    /** Runs only the player tick, preserving the historical compatibility mode. */
    PLAYER("Player", { player.tick() });

    fun tick() = tick.run()
}
