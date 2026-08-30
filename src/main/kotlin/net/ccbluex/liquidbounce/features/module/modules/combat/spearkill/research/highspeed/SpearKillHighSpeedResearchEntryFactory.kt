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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed


import net.minecraft.world.phys.Vec3

internal fun buildSpearKillHighSpeedResearchEntry(
    burst: ActiveSpearKillHighSpeedResearchBurst,
    currentTick: Int,
) = SpearKillHighSpeedResearchEntry(
    burstId = burst.id,
    timing = burst.timing(currentTick),
    packetPlan = burst.packetPlan(),
    movement = burst.movement(),
    sourcePrediction = burst.start.sourcePrediction(),
    delivery = burst.delivery(),
    correction = burst.correction,
    target = burst.targetEvidence(),
    outcome = when {
        burst.correction != null -> SpearKillHighSpeedResearchOutcome.CORRECTED
        burst.deliveryFailed -> SpearKillHighSpeedResearchOutcome.DELIVERY_FAILED
        else -> SpearKillHighSpeedResearchOutcome.NO_CORRECTION_OBSERVED
    },
)

private fun ActiveSpearKillHighSpeedResearchBurst.timing(currentTick: Int) =
    SpearKillHighSpeedResearchTiming(
        startedAtEpochMs = startedAtEpochMs,
        completedAtEpochMs = System.currentTimeMillis(),
        startedAtMonotonicNanos = startedAtMonotonicNanos,
        completedAtMonotonicNanos = System.nanoTime(),
        clientTick = start.clientTick,
        completionTick = currentTick,
    )

private fun ActiveSpearKillHighSpeedResearchBurst.packetPlan() = SpearKillHighSpeedResearchPacketPlan(
    primingPacketsRequested = start.primingPacketsRequested,
    primingPacketsSent = primingPacketsSent,
    primingPacketType = start.primingPacketType,
    finalPacketType = start.finalPacketType,
    noFallPacketsSent = noFallPacketsSent,
    packetBudget = start.packetBudget,
)

private fun ActiveSpearKillHighSpeedResearchBurst.movement(): SpearKillHighSpeedResearchMovement {
    val request = start
    return SpearKillHighSpeedResearchMovement(
        origin = request.origin.toResearchVector(),
        destination = request.destination.toResearchVector(),
        localPositionBefore = request.localPositionBefore.toResearchVector(),
        observedLocalPosition = observedLocalPosition?.toResearchVector(),
        requestedDistance = request.origin.distanceTo(request.destination),
        observedLocalDisplacement = observedLocalPosition?.distanceTo(request.localPositionBefore),
        targetSpeed = request.targetSpeed,
        currentSpeed = request.currentSpeed,
        acceleration = request.acceleration,
        deceleration = request.deceleration,
        routeStepLimit = request.routeStepLimit,
        expectedVelocity = request.expectedVelocity,
        elytraFlying = request.elytraFlying,
        onGround = request.onGround,
        horizontalCollision = request.horizontalCollision,
        corridorBlocked = request.corridorBlocked,
        destinationSpaceFree = request.destinationSpaceFree,
        terminalRaytraceClear = request.terminalRaytraceClear,
    )
}

private fun SpearKillHighSpeedResearchBurstStart.sourcePrediction(): SpearKillHighSpeedResearchSourcePrediction {
    val expectedVelocitySquared = expectedVelocity * expectedVelocity
    return SpearKillHighSpeedResearchSourcePrediction(
        squaredDistanceThresholdPerPacket = squaredDistanceThresholdPerPacket,
        expectedVelocitySquared = expectedVelocitySquared,
        effectivePacketCount = effectivePacketCount,
        packetCountReset = packetCountReset,
        predictedMaximumDistance = kotlin.math.sqrt(
            expectedVelocitySquared + squaredDistanceThresholdPerPacket * effectivePacketCount,
        ),
        predictedAccepted = predictedAccepted,
    )
}

private fun ActiveSpearKillHighSpeedResearchBurst.delivery() = SpearKillHighSpeedResearchDelivery(
    primingPacketsDelivered = primingPacketsDelivered,
    finalPacketDelivered = finalPacketDelivered,
    blinkQueued = blinkQueued,
    tickEndPacketsSuppressed = tickEndPacketsSuppressed,
    tickEndBoundariesObserved = tickEndBoundariesObserved,
)

private fun ActiveSpearKillHighSpeedResearchBurst.targetEvidence(): SpearKillHighSpeedResearchTargetEvidence? =
    start.target?.let { target ->
        val healthAfter = targetHealthAfter ?: target.health
        SpearKillHighSpeedResearchTargetEvidence(
            entityId = target.entityId,
            name = target.name,
            healthBefore = target.health,
            healthAfter = healthAfter,
            observedHealthDelta = (target.health - healthAfter).coerceAtLeast(0.0),
            damageEventObserved = damageEventObserved,
            damageEventAmount = null,
            deathObserved = targetDeathObserved,
            estimatedKineticDamage = target.estimatedKineticDamage,
        )
    }

private fun Vec3.toResearchVector() = SpearKillHighSpeedResearchVector(x, y, z)
