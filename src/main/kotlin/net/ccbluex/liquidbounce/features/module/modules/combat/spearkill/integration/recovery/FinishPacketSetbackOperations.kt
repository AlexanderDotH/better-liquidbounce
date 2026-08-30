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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.startPacketFirstReturnRecovery
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player

internal fun SpearKillModuleState.finishPacketSetback(packet: ClientboundPlayerPositionPacket, player: Player) {
    val setback = setbackRollback.finish(packet)
    if (setback == null) {
        if (player.isPassenger && setbackRollback.isMarked(packet)) clearAttack()
        return
    }

    setback.localState.restore(player)
    setbackGuard.clear()
    val authoritativePosition = setback.sessionOrigin.add(setback.authoritativeOffset)
    val correctionDescent = pendingSetbackConfirmedOffset
        ?.let { confirmed -> (confirmed.y - setback.authoritativeOffset.y).coerceAtLeast(0.0) }
        ?: 0.0
    val recoveryFallDistance = maxOf(
        pendingSetbackFallDistance ?: 0.0,
        player.fallDistance.toDouble(),
    ) + correctionDescent
    debugSpearKill("SETBACK_CONFIRMED") {
        listOf(
            "tick" to player.tickCount,
            "authoritative_position" to spearKillDebugVector(authoritativePosition),
            "authoritative_offset" to spearKillDebugVector(setback.authoritativeOffset),
            "correction_descent" to correctionDescent,
            "recovery_fall_distance" to recoveryFallDistance,
            "exact_recovery_steps" to setback.exactRecoveryMovements?.size,
        ) + spearKillDebugSessionFields()
    }
    pendingSetbackFallDistance = null
    pendingSetbackConfirmedOffset = null
    startPacketFirstReturnRecovery(
        authoritativePosition = authoritativePosition,
        targetPlayer = player,
        preferredFirstLeg = setback.exactRecoveryMovements,
        initialFallDistance = recoveryFallDistance,
    )
    synchronizeSpearKillServerSneak()
}
