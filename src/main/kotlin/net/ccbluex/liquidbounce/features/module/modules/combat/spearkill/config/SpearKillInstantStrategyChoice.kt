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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SPEAR_KILL_PACKET_MAX_WAIT_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup

/** Owns SpearKill's movement-mode schema independently from its attack runtime. */
internal sealed class SpearKillInstantStrategyChoice(
    name: String,
    final override val parent: ModeValueGroup<SpearKillInstantStrategyChoice>,
) : Mode(name)

internal class SpearKillInstantSafe(
    parent: ModeValueGroup<SpearKillInstantStrategyChoice>,
) : SpearKillInstantStrategyChoice(
    name = "Safe",
    parent = parent,
)

/** PlayerMove packet shape used to advance the same-tick server packet counter. */
internal enum class SpearKillPrimedInstantPacketType(override val tag: String) : Tagged {
    Position("Position"),
    PositionRotation("PositionRotation"),
    Rotation("Rotation"),
    StatusOnly("StatusOnly"),
}

/** Matches vanilla's basic preconditions before SpearKill asks the server to start fall flying. */
internal fun canStartSpearKillElytraFlight(
    isFallFlying: Boolean,
    hasFlyingAbility: Boolean,
    isPassenger: Boolean,
    isOnClimbable: Boolean,
    isInWater: Boolean,
    hasLevitation: Boolean,
    isOnGround: Boolean,
    hasUsableElytra: Boolean,
): Boolean = hasUsableElytra && !hasFlyingAbility && !isPassenger && !isOnClimbable &&
    !isInWater && !hasLevitation && (isFallFlying || !isOnGround)

internal const val SPEAR_KILL_MIN_SPEED = 2f
internal const val SPEAR_KILL_MIN_TARGET_SPEED = 1f
internal const val SPEAR_KILL_MIN_SPEED_CHANGE = 0.1f
internal const val SPEAR_KILL_NORMAL_MAX_SPEED = 10f
internal const val SPEAR_KILL_ELYTRA_MAX_SPEED = 17.32f
internal const val SPEAR_KILL_EXPERIMENTAL_MAX_SPEED = 500f
internal const val SPEAR_KILL_MAX_WAIT_TICKS = SPEAR_KILL_PACKET_MAX_WAIT_TICKS
internal const val SPEAR_KILL_INSTANT_DEFAULT_MAX_PACKETS = 128
internal const val SPEAR_KILL_INSTANT_MIN_MAX_PACKETS = 2
internal const val SPEAR_KILL_INSTANT_MAX_MAX_PACKETS = 512
internal const val SPEAR_KILL_INSTANT_SERVER_EVALUATION_TICKS = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS
internal const val SPEAR_KILL_INSTANT_DAMAGE_SAMPLE_TICKS = 1
