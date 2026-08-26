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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

/** Resolves the first safe plan while preserving projectile, mace, then spear priority. */
internal fun <T> selectAutoDodgePacketCandidate(
    projectile: () -> T?,
    mace: () -> T?,
    spear: () -> T?,
): T? = projectile() ?: mace() ?: spear()

internal fun selectDueAutoDodgePacketCandidate(
    candidates: List<AutoDodgePacketCandidate>,
    tick: Long,
): AutoDodgePacketCandidate? = selectAutoDodgePacketCandidate(
    projectile = { candidates.due(AutoDodgePacketThreatType.PROJECTILE, tick) },
    mace = { candidates.due(AutoDodgePacketThreatType.MACE, tick) },
    spear = { candidates.due(AutoDodgePacketThreatType.SPEAR, tick) },
)

internal fun selectArmedAutoDodgePacketCandidate(
    candidates: List<AutoDodgePacketCandidate>,
    tick: Long,
): AutoDodgePacketCandidate? = candidates.asSequence()
    .filterNot { it.impactSchedule.isDodgeDue(tick) }
    .minWithOrNull(
        compareBy<AutoDodgePacketCandidate> { it.impactSchedule.dodgeAtTick }
            .thenBy { it.threatType.ordinal }
            .thenBy { it.threatKey.entityId }
    )

private fun List<AutoDodgePacketCandidate>.due(
    threatType: AutoDodgePacketThreatType,
    tick: Long,
): AutoDodgePacketCandidate? = asSequence()
    .filter { it.threatType == threatType && it.impactSchedule.isDodgeDue(tick) }
    .minByOrNull { it.threatKey.entityId }
