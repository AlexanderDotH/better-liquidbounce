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

package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object PlayerModelParticleHook {

    private const val PROXIMITY_DISTANCE = 0.75
    private const val PROXIMITY_DISTANCE_SQ = PROXIMITY_DISTANCE * PROXIMITY_DISTANCE
    private const val MIN_OFFSET_SQ = 0.01 * 0.01

    @JvmStatic
    fun shouldRedirectEntityParticles(entity: Entity): Boolean {
        if (!ModuleAmnesia.running) {
            return false
        }

        val target = ModuleAmnesia.findTarget() ?: return false
        return entity.id == target.id && getActiveOffset() != null
    }

    @JvmStatic
    fun getEntityParticleOffset(@Suppress("UNUSED_PARAMETER") entity: Entity): Vec3? {
        return getActiveOffset()
    }

    @JvmStatic
    fun getParticleRedirectOffset(x: Double, y: Double, z: Double): Vec3? {
        val offset = getActiveOffset() ?: return null

        val target = ModuleAmnesia.findTarget() ?: return null
        val particle = Vec3(x, y, z)

        val realBounds = target.boundingBox
        if (realBounds.distanceToSqr(particle) > PROXIMITY_DISTANCE_SQ) {
            return null
        }

        val delayedBounds = realBounds.move(offset)
        if (delayedBounds.distanceToSqr(particle) <= realBounds.distanceToSqr(particle)) {
            return null
        }

        return offset
    }

    private fun getActiveOffset(): Vec3? {
        if (!ModuleAmnesia.running) {
            return null
        }

        val target = ModuleAmnesia.findTarget() ?: return null
        val visualPos = ModuleAmnesia.getVisualTransform(target)?.position ?: return null
        val offset = visualPos.subtract(target.position())
        return offset.takeIf { offset.lengthSqr() > MIN_OFFSET_SQ }
    }

    private fun AABB.move(offset: Vec3): AABB = move(offset.x, offset.y, offset.z)
}
