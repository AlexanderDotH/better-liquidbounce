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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.hasClearSpearKillDirectTravel(direction: Vec3, travel: Double): Boolean {
    val normalizedDirection = direction.normalize()
    if (normalizedDirection.lengthSqr() == 0.0) return false

    val origin = player.position()
    val destination = origin.add(normalizedDirection.scale(travel))
    return createFastSpearKillSegmentValidator(
        origin = origin,
        playerBoundingBox = spearKillServerCollisionBoxAt(origin),
    ).isClear(origin, destination)
}
