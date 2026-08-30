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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.fastutil.filterIsInstance
import net.ccbluex.fastutil.weightedMinByOrNullAtMost
import net.ccbluex.liquidbounce.features.blink.TrackedEntityPosition
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspData
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal data class VelocityReduceTargets<T : Any>(
    val attackTarget: T?,
    val renderTarget: T?,
)

internal interface VelocityReduceTargetSearch<T : Any> {
    fun isWithinLagStart(target: T, maxSquaredDistance: Float): Boolean
    fun findCrosshair(maxRange: Double): T?
    fun findFallback(maxSquaredDistance: Double): T?
}

internal class VelocityReduceTargetSelector<T : Any>(
    private val search: VelocityReduceTargetSearch<T>,
) {
    fun select(
        current: VelocityReduceTargets<T>,
        canLag: Boolean,
        lagging: Boolean,
        killAuraTarget: T?,
        lagRange: ClosedFloatingPointRange<Float>,
        interactionRange: Double,
    ): VelocityReduceTargets<T> {
        if (!canLag && lagging) return current
        return killAuraTarget?.let { selectKillAura(current, canLag, lagging, it, lagRange.start.sq()) }
            ?: selectCrosshair(current, canLag, lagging, lagRange, interactionRange)
    }

    private fun selectKillAura(
        current: VelocityReduceTargets<T>,
        canLag: Boolean,
        lagging: Boolean,
        killAuraTarget: T,
        lagStartSquared: Float,
    ): VelocityReduceTargets<T> {
        val attackTarget = if (!canLag || search.isWithinLagStart(killAuraTarget, lagStartSquared)) {
            killAuraTarget
        } else {
            current.attackTarget
        }
        val renderTarget = if (lagging) current.renderTarget else killAuraTarget
        return VelocityReduceTargets(attackTarget, renderTarget)
    }

    private fun selectCrosshair(
        current: VelocityReduceTargets<T>,
        canLag: Boolean,
        lagging: Boolean,
        lagRange: ClosedFloatingPointRange<Float>,
        interactionRange: Double,
    ): VelocityReduceTargets<T> {
        val maxRange = if (canLag) lagRange.start.toDouble() else interactionRange
        val attackTarget = search.findCrosshair(maxRange)
        val renderTarget = when {
            lagging -> current.renderTarget
            attackTarget != null -> attackTarget
            else -> search.findFallback(lagRange.endInclusive.sq().toDouble())
        }
        return VelocityReduceTargets(attackTarget, renderTarget)
    }
}

internal class VelocityReduceTargetTracker {
    private var targets = VelocityReduceTargets<Entity>(null, null)
    private var trackedRenderPosition: TrackedEntityPosition? = null

    val attackTarget: Entity?
        get() = targets.attackTarget
    val renderTarget: Entity?
        get() = targets.renderTarget
    val renderPosition: Vec3
        get() = trackedRenderPosition?.base ?: Vec3.ZERO

    fun reset() {
        targets = VelocityReduceTargets(null, null)
        trackedRenderPosition = null
    }

    fun resetRenderState() {
        targets = targets.copy(renderTarget = null)
        trackedRenderPosition = null
    }

    fun clearAttackTarget() {
        targets = targets.copy(attackTarget = null)
    }

    fun find(
        player: LocalPlayer,
        world: ClientLevel,
        canLag: Boolean,
        lagging: Boolean,
        lagRange: ClosedFloatingPointRange<Float>,
    ) {
        val killAuraTarget = if (ModuleKillAura.running) ModuleKillAura.targetTracker.target else null
        targets = VelocityReduceTargetSelector(EntityTargetSearch(player, world)).select(
            current = targets,
            canLag = canLag,
            lagging = lagging,
            killAuraTarget = killAuraTarget,
            lagRange = lagRange,
            interactionRange = ModuleKillAura.range.interactionRange.toDouble(),
        )
    }

    fun trackRenderPosition() {
        trackedRenderPosition = TrackedEntityPosition(renderTarget!!)
    }

    fun handlePacket(packet: Packet<*>, world: ClientLevel) {
        val trackedPosition = trackedRenderPosition
        val trackedTarget = renderTarget
        if (trackedPosition != null && trackedTarget != null) {
            trackedPosition.handlePacket(packet, world, trackedTarget)
        }
    }

    fun getEspData(lagging: Boolean): BlinkEspData? {
        if (!lagging) return null
        val trackedTarget = renderTarget ?: return null
        val trackedPosition = trackedRenderPosition ?: return null
        return BlinkEspData(trackedTarget, trackedPosition.base, trackedTarget.rotation)
    }

    fun hasLostReduceTarget(): Boolean {
        val reduceTarget = attackTarget ?: return true
        if (!ModuleKillAura.running) return false
        val killAuraTarget = ModuleKillAura.targetTracker.target ?: return true
        return killAuraTarget.id != reduceTarget.id
    }
}

private class EntityTargetSearch(
    private val player: LocalPlayer,
    private val world: ClientLevel,
) : VelocityReduceTargetSearch<Entity> {
    override fun isWithinLagStart(target: Entity, maxSquaredDistance: Float) =
        target.squaredBoxedDistanceTo(player) <= maxSquaredDistance

    override fun findCrosshair(maxRange: Double): Entity? = findEntityInCrosshair(
        maxRange,
        RotationManager.currentRotation ?: player.rotation,
    ) { !it.isRemoved && it.shouldBeAttacked() }?.entity

    override fun findFallback(maxSquaredDistance: Double): Entity? =
        world.entitiesForRendering().filterIsInstance<LivingEntity> { entity ->
            !entity.isRemoved && entity.shouldBeAttacked()
        }.weightedMinByOrNullAtMost(maxSquaredDistance) { entity ->
            entity.squaredBoxedDistanceTo(player)
        }
}
