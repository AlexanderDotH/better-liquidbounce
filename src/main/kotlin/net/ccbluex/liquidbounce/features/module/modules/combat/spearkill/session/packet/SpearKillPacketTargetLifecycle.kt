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

@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet



internal enum class SpearKillPacketTargetState {
    ACTIVE,
    DEFEATED,
    UNREACHABLE,
}

internal fun classifySpearKillPacketTargetState(
    isAlive: Boolean,
    isRemoved: Boolean,
    isInCurrentWorld: Boolean,
    isWithinRange: Boolean,
): SpearKillPacketTargetState = when {
    !isAlive -> SpearKillPacketTargetState.DEFEATED
    isRemoved || !isInCurrentWorld || !isWithinRange -> SpearKillPacketTargetState.UNREACHABLE
    else -> SpearKillPacketTargetState.ACTIVE
}
