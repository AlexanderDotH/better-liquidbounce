/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
internal fun <T : Any> selectUsableSpearKillAStarReplan(
    plan: T?,
    damageUseDuration: Int?,
    hasDamageWindow: (T, Int) -> Boolean,
): T? {
    val availablePlan = plan ?: return null
    val availableDuration = damageUseDuration ?: return null
    return availablePlan.takeIf { hasDamageWindow(availablePlan, availableDuration) }
}
