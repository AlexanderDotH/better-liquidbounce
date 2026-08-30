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

package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal class TargetStrafeRenderState {
    var target: LivingEntity? = null
    var orbitRadius = 0F
    var nextPoint: Vec3 = Vec3.ZERO
    var nextPointValid = false

    fun update(target: LivingEntity, orbitRadius: Float, nextPoint: Vec3, nextPointValid: Boolean) {
        this.target = target
        this.orbitRadius = orbitRadius
        this.nextPoint = nextPoint
        this.nextPointValid = nextPointValid
    }

    fun reset() {
        target = null
        orbitRadius = 0F
        nextPoint = Vec3.ZERO
        nextPointValid = false
    }
}
