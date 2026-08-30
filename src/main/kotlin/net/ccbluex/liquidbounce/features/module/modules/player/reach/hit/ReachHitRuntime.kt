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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.ReachHitCombatBridge
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.AStarPathBuilder
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.entity.hasCooldown
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.features.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent

internal class ReachHitRuntime(
    internal val owner: ReachHit,
) : MinecraftShortcuts, AStarPathBuilder {

    override val allowDiagonal: Boolean
        get() = owner.allowDiagonal

    internal var desyncPlayerPosition: Vec3? = null
    internal var hoverTarget: LivingEntity? = null
    internal var isExecuting = false
    internal var setbackDetected = false
    internal var executionGeneration = 0L
    internal val executionMode = ReachHitExecutionMode()
    internal val retryGate = ReachHitAutomaticRetryGate(REACH_HIT_AUTOMATIC_RETRY_DELAY_TICKS)

    internal fun updateHover() {
        if (!isExecuting) {
            hoverTarget = resolveCrosshairTarget()
        }
    }

    internal fun interceptAttackKey(event: KeybindIsPressedEvent) {
        if (event.keyBinding != mc.options.keyAttack || isExecuting) return
        if (resolveCrosshairTarget() == null || !isAttackReady()) return
        event.isPressed = false
    }

    internal suspend fun tickAttack() {
        if (!mc.options.keyAttack.wasPressedRecently(250)) return
        val target = resolveCrosshairTarget() ?: return
        tryAttack(target, player.rotation, keepSprint = true, automatedByKillAura = false)
    }

    internal fun handlePacket(packet: Packet<*>) {
        val travelMode = executionMode.current ?: return
        when (packet) {
            is ServerboundMovePlayerPacket -> rewriteMovement(packet, travelMode)
            is ClientboundPlayerPositionPacket -> recordSetback(travelMode)
        }
    }

    private fun rewriteMovement(packet: ServerboundMovePlayerPacket, travelMode: ReachHitMode) {
        if (!travelMode.usesPacketTravel) return
        val position = desyncPlayerPosition ?: return
        packet.x = position.x
        packet.y = position.y
        packet.z = position.z
        packet.hasPos = true
    }

    private fun recordSetback(travelMode: ReachHitMode) {
        if (!travelMode.usesPacketTravel || !isExecuting && desyncPlayerPosition == null || setbackDetected) return
        setbackDetected = true
        desyncPlayerPosition = null
        if (travelMode != ReachHitMode.ADAPTIVE) {
            chat(markAsError("Server setback detected, Reach Hit failed!"))
        }
    }

    internal fun renderTracer(event: WorldRenderEvent) {
        if (!shouldRenderReachHitTracer(owner.tracers, hoverTarget != null)) return
        val target = hoverTarget ?: return
        event.renderEnvironment {
            val cameraPosition = camera.position()
            drawLine(
                player.position().add(0.0, 1.0, 0.0).subtract(cameraPosition),
                target.position().add(0.0, 1.0, 0.0).subtract(cameraPosition),
                Color4b.WHITE.argb,
            )
        }
    }

    internal fun disable() {
        executionGeneration++
        desyncPlayerPosition = null
        hoverTarget = null
        setbackDetected = isExecuting
        retryGate.clear()
    }

    internal suspend fun tryAttack(
        target: LivingEntity,
        rotation: Rotation,
        keepSprint: Boolean,
        automatedByKillAura: Boolean,
    ): Boolean {
        val attemptTick = player.tickCount
        if (automatedByKillAura && !retryGate.canAttempt(target.id, attemptTick)) return false
        if (!canStartAttack(target, rotation)) return false
        val lease = RemoteMovementOwnership.tryAcquire(REACH_HIT_MOVEMENT_OWNER) ?: return false
        var success = false
        try {
            success = startAttack(target, rotation, keepSprint)
        } finally {
            try {
                finishAttack(target.id, attemptTick, automatedByKillAura, success)
            } finally {
                lease.close()
            }
        }
        return success
    }

    private suspend fun startAttack(target: LivingEntity, rotation: Rotation, keepSprint: Boolean): Boolean {
        isExecuting = true
        setbackDetected = false
        val travelMode = executionMode.capture(owner.modeChoice.activeMode.travelMode)
        return executeAttack(
            target,
            player.position(),
            target.position(),
            rotation,
            keepSprint,
            executionGeneration,
            travelMode,
        )
    }

    private fun finishAttack(targetId: Int, tick: Int, automated: Boolean, success: Boolean) {
        desyncPlayerPosition = null
        isExecuting = false
        setbackDetected = false
        executionMode.clear()
        if (!automated) return
        if (success) retryGate.recordSuccess() else retryGate.recordFailure(targetId, tick)
    }

    private fun canStartAttack(target: LivingEntity, rotation: Rotation): Boolean {
        if (!owner.running || isExecuting || !isAttackReady() || !owner.isTargetInConfiguredRange(target)) return false
        if (!target.isAlive || target.isRemoved || !ReachHitCombatBridge.shouldAttack(target)) return false
        return isLookingAtEntity(
            toEntity = target,
            rotation = rotation,
            range = owner.maxRange.toDouble(),
            throughWallsRange = 0.0,
        ) != null
    }

    internal fun isExecutionActive(generation: Long): Boolean =
        owner.running && isExecuting && !setbackDetected && executionGeneration == generation

    private fun isAttackReady() =
        isReachHitAttackReady(player.hasCooldown, player.getAttackStrengthScale(0.5f))

    private fun resolveCrosshairTarget(): LivingEntity? {
        val camera = mc.cameraEntity ?: return null
        val hitResult = findEntityInCrosshair(owner.maxRange.toDouble(), player.rotation) ?: return null
        val entity = hitResult.entity as? LivingEntity ?: return null
        val distanceSq = player.squaredBoxedDistanceTo(entity)
        if (!ReachHitCombatBridge.shouldAttack(entity) ||
            distanceSq <= owner.minRange.sq() || distanceSq > owner.maxRange.sq()
        ) {
            return null
        }
        return entity.takeIf { hasLineOfSight(camera.eyePosition, hitResult.location, camera) }
    }
}
