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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillLocalPlayerState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearVirtualMovementState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.createCollisionSafeSetbackRecovery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.physicalReturnConfigured
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.preparePacketSetback(packet: ClientboundPlayerPositionPacket, player: Player) {
    if (!setbackRollback.isMarked(packet)) return
    attemptTracker.markSetback()
    val rejectedRouteTarget = lockedAStarTarget
    logSpearKillSetbackPrepare(packet, player, rejectedRouteTarget)
    if (rejectPassengerSetback(player, rejectedRouteTarget)) return

    val localState = SpearKillLocalPlayerState.capture(player)
    val sessionOrigin = packetSessionOrigin ?: returnRecoveryTracker.recoveryOrigin ?: player.position()
    observeSetbackOrigin(localState, sessionOrigin)
    val preparedSetback = setbackRollback.prepare(
        packet,
        localState,
        setbackGuard,
        physicalReturn = packetBootSession.physicalReturnConfigured,
        sessionOrigin = sessionOrigin,
        exactRecoveryMovementsFor = { offset ->
            createCollisionSafeSetbackRecovery(sessionOrigin, offset)
        },
    )
    if (preparedSetback == null) {
        rejectUnrecoverableSetback(player, sessionOrigin, rejectedRouteTarget)
        return
    }
    armPreparedSetback(player, rejectedRouteTarget)
    logSpearKillSetbackRecoveryArmed(player, preparedSetback)
}

private fun SpearKillModuleState.rejectPassengerSetback(player: Player, rejectedRouteTarget: LivingEntity?): Boolean {
    if (!player.isPassenger) return false
    clearAttack("setback-passenger")
    rejectedRouteTarget?.let(::rejectSpearKillTarget)
    return true
}

private fun SpearKillModuleState.observeSetbackOrigin(localState: SpearKillLocalPlayerState, sessionOrigin: Vec3) {
    if (returnRecoveryTracker.recoveryOrigin == null) returnRecoveryTracker.begin(sessionOrigin)
    if (!physicalReturnPositioner.followingReturn) {
        returnRecoveryTracker.observeCombatPosition(localState.movement.position)
    }
}

private fun SpearKillModuleState.rejectUnrecoverableSetback(
    player: Player,
    sessionOrigin: Vec3,
    rejectedRouteTarget: LivingEntity?,
) {
    logSpearKillSetbackRecoveryFailed(player, sessionOrigin, rejectedRouteTarget)
    pendingSetbackFallDistance = null
    pendingSetbackConfirmedOffset = null
    clearAttack("setback-unrecoverable")
    rejectedRouteTarget?.let(::rejectSpearKillTarget)
}

private fun SpearKillModuleState.armPreparedSetback(
    player: Player,
    rejectedRouteTarget: LivingEntity?,
) {
    val recoverySettings = packetSessionSettings
    if (recoverySettings?.networkOptimized == true) {
        networkOptimizer.recordSetback(
            currentTick = player.tickCount,
            backoffTicks = recoverySettings.setbackBackoffTicks,
        )
    }
    pendingSetbackFallDistance = maxOf(
        player.fallDistance.toDouble(),
        fallSafetyLifecycle.confirmedFallDistance.takeIf { fallSafetyLifecycle.active } ?: 0.0,
    )
    pendingSetbackConfirmedOffset = packetBootSession.committedOffset
    clearVirtualMovementState(
        retainPrimedPacketBudget = recoverySettings?.primedInstant == true,
        retainRemoteKillOwnership = true,
    )
    physicalReturnPositioner.clear()
    packetRecoveryStallTicks = 0
    rejectedRouteTarget?.let(::rejectSpearKillTarget)
    synchronizeSpearKillServerSneak()
    packetSessionSettings = recoverySettings
    activeMovementTransport = recoverySettings?.transport
    packetSetbackRecoveryAttempted = true
}
