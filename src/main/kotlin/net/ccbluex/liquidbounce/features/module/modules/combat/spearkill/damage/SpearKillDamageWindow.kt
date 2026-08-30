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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.minecraft.world.phys.Vec3

internal fun spearKillSessionAbortSnapPosition(
    sessionOrigin: Vec3?,
    committedOffset: Vec3,
    physicalReturnConfigured: Boolean,
): Vec3? {
    if (sessionOrigin == null) return null
    val offsetFinite = committedOffset.x.isFinite() &&
        committedOffset.y.isFinite() &&
        committedOffset.z.isFinite()
    if (!offsetFinite) return sessionOrigin
    if (committedOffset.lengthSqr() > 1.0E-12 || physicalReturnConfigured) {
        return sessionOrigin
    }
    return null
}

/** Validates that a schedule hit tick still fits inside the kinetic spear's remaining damage window. */
internal fun hasSpearKillAStarDamageWindow(
    ticksUsingItem: Int,
    damageUseDuration: Int,
    outboundStepCount: Int,
    stepWaitTicks: Int,
    confirmationTicks: Int,
    preStrikeHoldTicks: Int = 0,
    terminalSuffixCount: Int = 1,
): Boolean {
    val schedule = buildSpearKillPathSchedule(
        outboundStepCount = outboundStepCount,
        stepWaitTicks = stepWaitTicks,
        terminalSuffixCount = terminalSuffixCount.coerceIn(1, outboundStepCount.coerceAtLeast(1)),
        preStrikeHoldTicks = preStrikeHoldTicks,
        strikeHoldTicks = confirmationTicks,
    ) ?: return false
    return hasSpearKillScheduleDamageWindow(
        ticksUsingItem = ticksUsingItem,
        damageUseDuration = damageUseDuration,
        hitTick = schedule.hitTick,
    )
}
