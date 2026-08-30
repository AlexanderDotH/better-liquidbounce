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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal object SpinbotCombatTargetResolver {

    fun find(target: LivingEntity, partialTicks: Float, attackRange: Float): LivingEntity? {
        val range = attackRange.coerceAtLeast(1f)
        val rotation = target.interpolateCurrentRotation(partialTicks)
        val rayHit = target.findEntityInCrosshair(range.toDouble(), rotation) { entity ->
            entity is LivingEntity && entity.id != target.id && isValidTarget(entity)
        }?.entity as? LivingEntity
        return rayHit ?: nearestTarget(target, range)
    }

    private fun nearestTarget(target: LivingEntity, range: Float): LivingEntity? {
        val rangeSq = range.sq()
        var best: LivingEntity? = null
        var bestDistance = Double.POSITIVE_INFINITY
        for (entity in world.entitiesForRendering()) {
            if (entity !is LivingEntity || entity.id == target.id || !isValidTarget(entity)) {
                continue
            }

            val distance = target.squaredBoxedDistanceTo(entity)
            if (distance > rangeSq || distance >= bestDistance) {
                continue
            }

            best = entity
            bestDistance = distance
        }
        return best
    }

    private fun isValidTarget(entity: LivingEntity): Boolean =
        !entity.isRemoved &&
            entity.isAlive &&
            !entity.isSpectator &&
            (entity !is Player || !ModuleAntiBot.isBot(entity))
}
