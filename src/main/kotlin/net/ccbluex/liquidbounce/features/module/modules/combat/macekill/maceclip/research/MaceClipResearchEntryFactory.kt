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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research



import net.minecraft.world.phys.Vec3

internal fun buildMaceClipResearchEntry(
    session: ActiveMaceClipResearchSession,
    currentTick: Int,
    observedLocalPosition: Vec3,
    exactReturnDelivered: Boolean,
    forcedFailure: Boolean,
): MaceClipResearchEntry {
    val delivery = session.deliveryEvidence(exactReturnDelivered)
    val deliveryFailed = forcedFailure || delivery.packetsQueued > 0 ||
        delivery.packetsCancelled > 0 || !exactReturnDelivered
    return MaceClipResearchEntry(
        sessionId = session.id,
        profile = session.start.profile,
        request = session.start.request,
        timing = session.timing(currentTick),
        phases = session.phases.map(ActiveMaceClipResearchPhase::toEvidence),
        packets = session.packets.toList(),
        corrections = session.corrections.toList(),
        positions = session.positions(observedLocalPosition),
        delivery = delivery,
        strike = MaceClipResearchStrikeEvidence(session.strikeAttempts, session.committedAttacks),
        target = session.targetEvidence(),
        abortRequested = session.abortRequested,
        outcome = session.outcome(deliveryFailed),
    )
}

private fun ActiveMaceClipResearchSession.deliveryEvidence(
    exactReturnDelivered: Boolean,
) = MaceClipResearchDeliveryEvidence(
    packetBudget = start.packetBudget,
    packetsSent = packets.size,
    packetsDelivered = packets.count { it.delivery == MaceClipResearchPacketDelivery.DELIVERED },
    packetsQueued = packets.count { it.delivery == MaceClipResearchPacketDelivery.QUEUED },
    packetsCancelled = packets.count { it.delivery == MaceClipResearchPacketDelivery.CANCELLED },
    exactReturnDelivered = exactReturnDelivered,
)

private fun ActiveMaceClipResearchSession.timing(currentTick: Int) = MaceClipResearchTiming(
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = System.currentTimeMillis(),
    startedAtMonotonicNanos = startedAtMonotonicNanos,
    completedAtMonotonicNanos = System.nanoTime(),
    clientTick = start.clientTick,
    completionTick = currentTick,
)

private fun ActiveMaceClipResearchSession.positions(localAfter: Vec3) = MaceClipResearchPositions(
    origin = start.origin.toResearchPosition(),
    target = start.targetPosition?.toResearchPosition(),
    attackEndpoint = start.attackEndpoint.toResearchPosition(),
    apex = start.apex.toResearchPosition(),
    localBefore = start.localPositionBefore.toResearchPosition(),
    localAfter = localAfter.toResearchPosition(),
    lastAuthoritativeCorrection = lastAuthoritativeCorrection?.toResearchPosition(),
    observedLocalDisplacement = start.localPositionBefore.distanceTo(localAfter),
)

private fun ActiveMaceClipResearchSession.targetEvidence(): MaceClipResearchTargetEvidence? =
    start.target?.let { target ->
        val healthAfter = targetHealthAfter ?: target.health
        MaceClipResearchTargetEvidence(
            entityId = target.entityId,
            name = target.name,
            healthBefore = target.health,
            healthAfter = healthAfter,
            observedHealthDelta = (target.health - healthAfter).coerceAtLeast(0.0),
            damageEventObserved = damageEventObserved,
            damageEventAmount = damageEventAmount,
            deathObserved = deathObserved,
        )
    }

private fun ActiveMaceClipResearchSession.outcome(deliveryFailed: Boolean) = when {
    corrections.isNotEmpty() -> MaceClipResearchOutcome.CORRECTED
    deliveryFailed -> MaceClipResearchOutcome.DELIVERY_FAILED
    abortRequested -> MaceClipResearchOutcome.ABORTED
    else -> MaceClipResearchOutcome.NO_CORRECTION_OBSERVED
}

private fun Vec3.toResearchPosition() = MaceClipResearchPosition(x, y, z)

private fun ActiveMaceClipResearchPhase.toEvidence() = MaceClipResearchPhaseEvidence(
    phase = phase,
    startedTick = startedTick,
    completedTick = completedTick,
    startPosition = startPosition.toResearchPosition(),
    endPosition = endPosition?.toResearchPosition(),
)
