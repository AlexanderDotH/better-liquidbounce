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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.cubecraft

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleClickTp
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerDispatcher
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePointValidation
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRuntime
import net.ccbluex.liquidbounce.utils.block.canStandOn
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.MODEL_STATE
import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.ccbluex.liquidbounce.utils.math.horizontalDistanceTo
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal object CubeCraftTargetStrafeMode : Mode("Cubecraft", aliases = listOf("CubePerfect")) {

    private val behindDistance by float("BehindDistance", 2f, 0.5f..5f, "blocks")
    private val searchRadius by int("SearchRadius", 2, 0..5, "blocks")
    private val retryDelay by int("RetryDelay", 10, 0..40, "ticks")
    private val tracker = CubeCraftTargetStrafeTracker()
    private var damageConfirmed = false
    private var wasHurt = false

    override fun enable() {
        resetTracking()
        super.enable()
    }

    override fun disable() {
        resetTracking()
        TargetStrafeRuntime.renderState.reset()
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val target = currentTarget() ?: return@tickHandler
        lockDestination(target)
        if (!revalidateLockedDestination()) {
            updateRenderState(target)
            return@tickHandler
        }
        updateRenderState(target)
        tracker.updatePosition(player.position(), ARRIVAL_DISTANCE)
        if (tracker.teleported) return@tickHandler
        observeDamage()
        val destination = tracker.takeTeleportRequest() ?: return@tickHandler
        val success = ModuleClickTp.teleportCubeCraftPacket(destination)
        tracker.completeTeleport(success)
        updateRenderState(target)
        if (!success) waitTicks(retryDelay)
    }

    private fun observeDamage() {
        val hurt = player.hurtTime > 0
        if (damageConfirmed || hurt && !wasHurt) tracker.confirmDamage()
        damageConfirmed = false
        wasHurt = hurt
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (!event.isCancelled && event.origin == TransferOrigin.INCOMING &&
            packet is ClientboundDamageEventPacket && packet.entityId == player.id) {
            damageConfirmed = true
        }
    }

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent>(priority = MODEL_STATE) { event ->
        if (!tracker.useInputFallback) return@handler
        TargetStrafePlannerDispatcher.handleInput(event)
        TargetStrafeRuntime.firstTarget()?.takeIf { tracker.hasLockFor(it.id) }?.let(::updateRenderState)
    }

    private fun currentTarget(): LivingEntity? {
        if (!TargetStrafeRuntime.requirementsMet) return resetAndNull()
        val target = TargetStrafeRuntime.firstTarget()
        if (target == null || target.isRemoved ||
            player.position().horizontalDistanceTo(target.position()) > TargetStrafeRuntime.followRange) {
            return resetAndNull()
        }
        return target
    }

    private fun resetAndNull(): LivingEntity? {
        resetTracking()
        TargetStrafeRuntime.renderState.reset()
        return null
    }

    private fun lockDestination(target: LivingEntity) {
        if (tracker.hasLockFor(target.id)) return
        if (!tracker.tracksTarget(target.id)) resetTracking()
        findTeleportDestination(target)?.let { tracker.lock(target.id, it) }
    }

    private fun revalidateLockedDestination(): Boolean {
        val destination = tracker.lockedDestination ?: return false
        if (TargetStrafePointValidation.validatePoint(destination)) return true
        tracker.invalidateLock()
        damageConfirmed = false
        wasHurt = player.hurtTime > 0
        return false
    }

    private fun findTeleportDestination(target: LivingEntity): Vec3? {
        val ideal = cubeCraftPositionBehind(target.position(), target.yHeadRot, behindDistance.toDouble())
        val center = BlockPos.containing(ideal.x, floor(target.y) - 1.0, ideal.z)
        for ((offsetX, offsetZ) in cubeCraftSearchOffsets(searchRadius)) {
            for (offsetY in SEARCH_Y_OFFSETS) {
                val ground = center.offset(offsetX, offsetY, offsetZ)
                if (!ground.canStandOn()) continue
                val destination = ground.bottomCenter(yOffset = 1.0)
                if (TargetStrafePointValidation.validatePoint(destination)) return destination
            }
        }
        return null
    }

    private fun updateRenderState(target: LivingEntity) {
        val locked = tracker.lockedDestination
        val point = locked ?: cubeCraftPositionBehind(target.position(), target.yHeadRot, behindDistance.toDouble())
        TargetStrafeRuntime.renderState.update(target, behindDistance, point, locked != null)
    }

    private fun resetTracking() {
        tracker.reset()
        damageConfirmed = false
        wasHurt = player.hurtTime > 0
    }

    private val SEARCH_Y_OFFSETS = intArrayOf(0, -1, 1, -2, 2)
    private const val ARRIVAL_DISTANCE = 0.5
}
