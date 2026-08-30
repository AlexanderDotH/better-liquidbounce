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
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal object HysteriaTargetResolver {

    fun findCombatPlayer(target: LivingEntity, partialTicks: Float, range: Float): Player? {
        val displayRotation = target.interpolateCurrentRotation(partialTicks)
        val rayHit = target.findEntityInCrosshair(range.toDouble(), displayRotation) { entity ->
            entity is Player && entity.id != target.id && isValid(entity)
        }?.entity as? Player
        if (rayHit != null) {
            return rayHit
        }

        val eyes = target.getEyePosition(partialTicks)
        val rangeSq = range.sq()
        var best: Player? = null
        var bestAngle = Float.MAX_VALUE
        for (entity in world.entitiesForRendering()) {
            if (entity !is Player || !isValid(entity) || entity.id == target.id) {
                continue
            }
            if (target.squaredBoxedDistanceTo(entity) > rangeSq) {
                continue
            }
            val rotation = Rotation.lookingAt(aimPoint(entity), eyes)
            val angle = displayRotation.rotationDeltaLengthTo(rotation).coerceAtMost(180f)
            if (angle < bestAngle) {
                bestAngle = angle
                best = entity
            }
        }
        return best?.takeIf { bestAngle <= COMBAT_LOOK_TOLERANCE }
    }

    fun collectCandidates(target: LivingEntity, range: Float): List<Player> {
        val rangeSq = range.sq()
        val candidates = world.entitiesForRendering()
            .filterIsInstance<Player>()
            .filterTo(ArrayList()) { player ->
                isValid(player) && player.id != target.id && target.squaredBoxedDistanceTo(player) <= rangeSq
            }
        val localPlayer = mc.player
        if (localPlayer != null &&
            localPlayer.id != target.id &&
            isValid(localPlayer) &&
            target.squaredBoxedDistanceTo(localPlayer) <= rangeSq &&
            candidates.none { it.id == localPlayer.id }
        ) {
            candidates.add(localPlayer)
        }
        return candidates
    }

    fun pickRandom(candidates: List<Player>, currentEntityId: Int?): Player? {
        if (candidates.size <= 1) {
            return candidates.firstOrNull()
        }
        if (currentEntityId != null) {
            candidates.filter { it.id != currentEntityId }.takeIf { it.isNotEmpty() }?.let { return it.random() }
        }
        return candidates.random()
    }

    fun aimAt(target: LivingEntity, entity: LivingEntity, partialTicks: Float): HysteriaModelRotation {
        val rotation = Rotation.lookingAt(aimPoint(entity), target.getEyePosition(partialTicks))
        return HysteriaModelRotation(rotation.yaw, rotation.yaw, rotation.pitch.coerceIn(-90f, 90f))
    }

    fun isValid(player: Player): Boolean =
        !player.isRemoved && player.isAlive && !player.isSpectator && !ModuleAntiBot.isBot(player)

    fun isInRange(target: LivingEntity, player: Player, range: Float): Boolean =
        target.squaredBoxedDistanceTo(player) <= range.sq()

    fun aimPoint(entity: LivingEntity) = entity.eyePosition

    private const val COMBAT_LOOK_TOLERANCE = 45f
}

internal data class HysteriaModelRotation(
    val bodyYaw: Float,
    val headYaw: Float,
    val pitch: Float,
)
