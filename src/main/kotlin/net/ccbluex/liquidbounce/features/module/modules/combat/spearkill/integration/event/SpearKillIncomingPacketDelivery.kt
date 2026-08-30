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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.SpearKillDamageEvidence
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.completeSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.packetPositionOrigin
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.spearKillCorrectionPosition
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.handleIncomingSpearKillPacket(event: PacketEvent) {
    when (val packet = event.packet) {
        is ClientboundDamageEventPacket -> handleSpearKillDamagePacket(packet, event.isCancelled)
        is ClientboundPlayerPositionPacket -> handleSpearKillCorrectionPacket(packet, event.isCancelled)
    }
}

private fun SpearKillModuleState.handleSpearKillDamagePacket(
    packet: ClientboundDamageEventPacket,
    cancelled: Boolean,
) {
    val evidenceArmed = damageEvidenceTracker.isArmed
    val target = world.getEntity(packet.entityId) as? LivingEntity
    if (cancelled) {
        if (evidenceArmed || target === lockedAStarTarget) {
            reportSpearKillDamagePacket(packet, target, true, evidenceArmed, null)
        }
        return
    }
    highSpeedResearch.recordDamageEvent(packet.entityId)
    val evidence = damageEvidenceTracker.observe(packet.entityId, player.tickCount)
    if (evidenceArmed || target === lockedAStarTarget || evidence != null) {
        reportSpearKillDamagePacket(packet, target, false, evidenceArmed, evidence)
    }
    if (evidence == null) return
    attemptTracker.markDamageEvidence()
    if (attemptRouteCompleted) {
        completeSpearKillAttempt("damage-evidence")
        attemptRouteCompleted = false
    }
}

private fun SpearKillModuleState.reportSpearKillDamagePacket(
    packet: ClientboundDamageEventPacket,
    target: LivingEntity?,
    cancelled: Boolean,
    evidenceArmed: Boolean,
    evidence: SpearKillDamageEvidence?,
) = debugSpearKill("DAMAGE_EVENT") {
    listOf(
        "tick" to player.tickCount,
        "entity_id" to packet.entityId,
        "cancelled" to cancelled,
        "evidence_window_armed" to evidenceArmed,
        "matched_attempt" to (evidence != null),
        "predicted_hit_tick" to evidence?.predictedHitTick,
        "observed_tick" to evidence?.observedTick,
    ) + spearKillDebugTargetFields(target, prefix = "damaged")
}

private fun SpearKillModuleState.handleSpearKillCorrectionPacket(
    packet: ClientboundPlayerPositionPacket,
    cancelled: Boolean,
) {
    val correctedPosition = spearKillCorrectionPosition(packet)
    if (isSpearKillCorrectionRelevant()) reportSpearKillCorrectionPacket(correctedPosition, cancelled)
    if (cancelled) return
    lastServerCorrectionTick = player.tickCount
    highSpeedResearch.recordCorrection(correctedPosition, player.tickCount)
    if (setbackGuard.armed) {
        speedController.rejectOutboundProgress()
        attemptTracker.markSetback()
        setbackRollback.mark(packet)
    }
}

private fun SpearKillModuleState.isSpearKillCorrectionRelevant(): Boolean =
    packetBootSession.active || setbackGuard.armed || attemptTracker.current != null ||
        highSpeedMoveProbeActive || packetSessionOrigin != null

private fun SpearKillModuleState.reportSpearKillCorrectionPacket(
    correctedPosition: Vec3,
    cancelled: Boolean,
) {
    val expectedPosition = packetPositionOrigin().add(packetBootSession.committedOffset)
    debugSpearKill("SERVER_CORRECTION") {
        listOf(
            "tick" to player.tickCount,
            "cancelled" to cancelled,
            "corrected_position" to spearKillDebugVector(correctedPosition),
            "expected_server_position" to spearKillDebugVector(expectedPosition),
            "local_position" to spearKillDebugVector(player.position()),
            "correction_from_expected" to correctedPosition.distanceTo(expectedPosition),
            "correction_from_local" to correctedPosition.distanceTo(player.position()),
            "setback_guard_armed" to setbackGuard.armed,
        ) + spearKillDebugTargetFields(lockedAStarTarget) + spearKillDebugSessionFields()
    }
}
