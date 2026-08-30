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
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.ownsClipReachRecoveryPackets(): Boolean = instantCorrectionRecoveryActive && routeEngine.ownsMovement

internal fun MaceKillModuleState.ownsVanillaVClipPendingStep(): Boolean {
    val origin = routeOrigin ?: return false
    if (routeSession.pendingMovement == null) return false
    val from = origin.add(routeSession.committedOffset)
    val to = origin.add(routeSession.virtualOffset)
    return activeVanillaVClipSegments.any { it.matches(from, to) }
}

internal fun MaceKillModuleState.activeMaceKillGroundPolicy(): MaceKillGroundPolicy = if (
    !ownsClipReachAnchorPackets() && !ownsClipReachRecoveryPackets()
) {
    MaceKillGroundPolicy.COLLISION_DERIVED
} else {
    MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF
}

internal fun MaceKillModuleState.activeMaceKillPacketKind(): MaceKillMovementPacketKind = when {
    ownsVanillaVClipPendingStep() -> MaceKillMovementPacketKind.VANILLA_VCLIP
    ownsClipReachRecoveryPackets() -> MaceKillMovementPacketKind.CLIP_REACH_RECOVERY
    ownsClipReachAnchorPackets() -> MaceKillMovementPacketKind.CLIP_REACH_ANCHOR
    activeRouteConfiguration?.routingMode == MaceKillRoutingMode.A_STAR ->
        MaceKillMovementPacketKind.ASTAR_ROUTE
    else -> MaceKillMovementPacketKind.DIRECT_ROUTE
}

internal fun MaceKillModuleState.maceKillRoutePacketGrounded(
    position: Vec3,
    identityOwnedByRoute: Boolean,
): Boolean {
    val packet = MaceKillGroundPacketContext(identityOwnedByRoute, activeMaceKillPacketKind())
    return shouldSpoofMaceKillVanillaVClipGround(packet) ||
        activeMaceKillGroundPolicy().shouldSpoofOnGround(packet) ||
        isMaceKillPositionNearGround(position)
}

internal fun MaceKillModuleState.createMaceKillMovementPacket(
    position: Vec3,
    onGround: Boolean,
): ServerboundMovePlayerPacket {
    val shape = researchExecution?.descriptor?.packetShape
    return when (shape) {
        MaceClipResearchPacketShape.POSITION_ROTATION -> ServerboundMovePlayerPacket.PosRot(
            position.x,
            position.y,
            position.z,
            player.yRot,
            player.xRot,
            onGround,
            player.horizontalCollision,
        )
        else -> ServerboundMovePlayerPacket.Pos(
            position.x,
            position.y,
            position.z,
            onGround,
            player.horizontalCollision,
        )
    }
}

internal fun MaceKillModuleState.attachResearchPacketContext(packet: ServerboundMovePlayerPacket, position: Vec3) {
    val execution = researchExecution ?: return
    val outbound = routeSession.pendingOutboundStep
    val movementIndex = if (outbound) execution.outboundDelivered else execution.returnDelivered
    val phase = execution.descriptor.phaseForMovement(outbound, movementIndex)
        ?: if (outbound) MaceClipResearchPhase.DESCEND else MaceClipResearchPhase.RETURN_DESCEND
    val context = MaceKillResearchPacketContext(
        sequence = execution.nextPacketSequence++,
        phase = phase,
        position = position,
        outbound = outbound,
    )
    researchPacketContexts[packet] = context
    val phaseStart = routeOrigin?.add(routeSession.committedOffset) ?: player.position()
    researchRuntime.recordPhaseStarted(execution.sessionId, phase, player.tickCount, phaseStart)
}
