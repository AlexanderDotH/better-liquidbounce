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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.findSpearKillAttackHitPoint
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.hasVisibleSpearKillAttackRay(
    eye: Vec3,
    direction: Vec3,
    targetBox: AABB,
    range: Double,
    lineOfSight: (Vec3, Vec3) -> Boolean = { from, to -> hasLineOfSight(from, to, player) },
): Boolean {
    val hitPoint = findSpearKillAttackHitPoint(eye, direction, targetBox, range) ?: return false
    return lineOfSight(eye, hitPoint)
}
