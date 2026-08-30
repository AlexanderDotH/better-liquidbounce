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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*

import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.MaceClipReachSessionOutcome
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

internal fun MaceKillModuleState.tickActiveRemoteRoute() {
    val target = routeEngine.activeTarget
    val targetAlive = target?.let { it.isAlive && !it.isRemoved && it.level() === world } == true
    val instantFailed = handleTerminalMaceKillInstantOutcome(targetAlive)
    if (!instantFailed) maintainMaceKillRouteTarget(target, targetAlive)
    handleMaceKillRouteStall()
    if (plannedRoutePacket != null || player.tickCount < routeResumeTick) return
    sendMaceKillRouteBatch()
    if (!routeEngine.ownsMovement) finishInactiveRouteOwnership()
}

private fun MaceKillModuleState.handleTerminalMaceKillInstantOutcome(targetAlive: Boolean): Boolean {
    val outcome = activeClipReachSession?.evaluate(player.tickCount.toLong(), targetAlive)
    val failed = outcome != null &&
        outcome != MaceClipReachSessionOutcome.ACTIVE &&
        outcome != MaceClipReachSessionOutcome.COMPLETED
    if (failed) {
        handleInstantSessionOutcome(requireNotNull(outcome))
    } else if (!routeSession.recovering && routeDeadlineTick != 0 && player.tickCount >= routeDeadlineTick) {
        routeRejected = true
        beginSafeRouteAbort()
    }
    return failed
}

private fun MaceKillModuleState.maintainMaceKillRouteTarget(
    target: LivingEntity?,
    targetAlive: Boolean,
) {
    if (!targetAlive) {
        if (target == null || activeClipReachSession != null || !tryStartTargetChain(target)) {
            beginSafeRouteAbort()
        }
    } else if (activeRouteOwner != MaceKillRouteOwner.RESEARCH) {
        replanMovingTargetBeforeStrike(requireNotNull(target))
    }
    if (routeEngine.awaitingStrike) handleRemoteStrikeResult(routeEngine.retryStrike())
}

private fun MaceKillModuleState.handleMaceKillRouteStall() {
    if (routeStallTicks < MACE_KILL_MAX_ROUTE_STALL_TICKS) return
    activeClipReachSession?.recordReplanRejected()?.let(::handleInstantSessionOutcome)
    beginSafeRouteAbort()
}

private fun MaceKillModuleState.sendMaceKillRouteBatch() {
    val timing = activeRouteConfiguration?.timing
    val correctionRecovery = activeClipReachSession?.outcome == MaceClipReachSessionOutcome.CORRECTED
    if (timing?.maxPacketsPerTick?.let { it > 1 } != true || motionRouteActive || correctionRecovery) {
        sendNextRoutePacket()
        return
    }
    repeat(timing.maxPacketsPerTick) {
        if (!routeEngine.ownsMovement || plannedRoutePacket != null || !sendNextRoutePacket()) return
    }
}

internal fun MaceKillModuleState.sendNextRoutePacket(): Boolean {
    val pending = prepareNextMaceKillRoutePacket() ?: return false
    val packet = createMaceKillMovementPacket(pending.position, pending.grounded)
    attachResearchPacketContext(packet, pending.position)
    plannedRoutePacket = packet
    network.send(packet)
    rejectUndeliveredMaceKillRoutePacket(packet)
    return true
}

private fun MaceKillModuleState.prepareNextMaceKillRoutePacket(): MaceKillPendingRoutePacket? {
    val origin = routeOrigin ?: return null
    val pendingOffset = routeEngine.prepareNextStep() ?: return null
    if (!routeEngine.ownsMovement) return null
    if (requiresMaceKillRouteValidation() && !validatePendingRouteStep(origin, pendingOffset)) {
        beginSafeRouteAbort()
        return null
    }
    val movement = routeSession.pendingMovement ?: return null
    val position = origin.add(pendingOffset)
    val grounded = maceKillRoutePacketGrounded(position, identityOwnedByRoute = true)
    if (!isMaceKillPendingMovementSafe(position, movement, grounded)) {
        beginSafeRouteAbort()
        return null
    }
    return MaceKillPendingRoutePacket(position, grounded)
}

private fun MaceKillModuleState.requiresMaceKillRouteValidation(): Boolean =
    shouldValidateMaceKillRouteSegment(
        clipAnchorOwned = ownsClipReachAnchorPackets(),
        clipRecoveryOwned = ownsClipReachRecoveryPackets(),
        researchOwned = researchExecution != null,
    )

private fun MaceKillModuleState.isMaceKillPendingMovementSafe(
    position: Vec3,
    movement: Vec3,
    grounded: Boolean,
): Boolean {
    val safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
    val projectedFallDistance = if (grounded && (ownsClipReachAnchorPackets() || ownsClipReachRecoveryPackets())) {
        0.0
    } else {
        projectedMaceKillFallDistance(fallSafetyLifecycle.confirmedFallDistance, movement)
    }
    if (!projectedFallDistance.isFinite() || !safeFallDistance.isFinite() || safeFallDistance < 0.0) return false
    if (isMaceKillPositionNearGround(position) && projectedFallDistance > safeFallDistance) return false
    return fallSafetyLifecycle.gatePendingMovement(
        movement,
        grounded,
    ) != MaceKillFallSafetyPendingStepGate.BLOCKED
}

private fun MaceKillModuleState.rejectUndeliveredMaceKillRoutePacket(packet: ServerboundMovePlayerPacket) {
    if (plannedRoutePacket !== packet) return
    val queued = BlinkManager.packetQueue.any { it.packet === packet }
    if (queued) BlinkManager.packetQueue.removeIf { it.packet === packet }
    recordResearchPacketDelivery(packet, delivered = false, queuedByBlink = queued)
    plannedRoutePacket = null
    routeEngine.confirmStep(delivered = false)
    routeStallTicks++
}

internal fun MaceKillModuleState.validatePendingRouteStep(origin: Vec3, candidateOffset: Vec3): Boolean {
    val from = origin.add(routeSession.committedOffset)
    val to = origin.add(candidateOffset)
    return createMaceKillSegmentValidator(
        origin = origin,
        originBoundingBox = routeOriginBoundingBox ?: player.boundingBox,
        allowedVanillaVClipSegments = activeVanillaVClipSegments,
    ).isClear(from, to)
}

private data class MaceKillPendingRoutePacket(
    val position: Vec3,
    val grounded: Boolean,
)
