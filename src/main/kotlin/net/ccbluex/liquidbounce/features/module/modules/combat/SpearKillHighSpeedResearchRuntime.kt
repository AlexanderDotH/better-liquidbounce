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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class SpearKillHighSpeedResearchTargetStart(
    val entityId: Int,
    val name: String,
    val health: Double,
    val estimatedKineticDamage: Double,
)

@Suppress("LongParameterList")
internal data class SpearKillHighSpeedResearchBurstStart(
    val clientTick: Int,
    val primingPacketsRequested: Int,
    val primingPacketType: SpearKillHighSpeedResearchPacketType,
    val finalPacketType: SpearKillHighSpeedResearchFinalPacketType,
    val packetBudget: Int,
    val origin: Vec3,
    val destination: Vec3,
    val localPositionBefore: Vec3,
    val targetSpeed: Double,
    val currentSpeed: Double,
    val acceleration: Double,
    val deceleration: Double,
    val routeStepLimit: Double,
    val expectedVelocity: Double,
    val elytraFlying: Boolean,
    val onGround: Boolean,
    val horizontalCollision: Boolean,
    val squaredDistanceThresholdPerPacket: Double,
    val effectivePacketCount: Int,
    val packetCountReset: Boolean,
    val predictedAccepted: Boolean,
    val corridorBlocked: Boolean,
    val destinationSpaceFree: Boolean,
    val terminalRaytraceClear: Boolean,
    val target: SpearKillHighSpeedResearchTargetStart?,
)

private data class ActiveSpearKillHighSpeedResearchBurst(
    val id: String,
    val start: SpearKillHighSpeedResearchBurstStart,
    val startedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    var primingPacketsSent: Int = 0,
    var primingPacketsDelivered: Int = 0,
    var noFallPacketsSent: Int = 0,
    var finalPacketSent: Boolean = false,
    var finalPacketDelivered: Boolean = false,
    var finalPacketTick: Int? = null,
    var blinkQueued: Boolean = false,
    var tickEndPacketsSuppressed: Int = 0,
    var tickEndBoundariesObserved: Int = 0,
    var correction: SpearKillHighSpeedResearchCorrection? = null,
    var targetHealthAfter: Double? = start.target?.health,
    var damageEventObserved: Boolean = false,
    var targetDeathObserved: Boolean = false,
    var deliveryFailed: Boolean = false,
    var observedLocalPosition: Vec3? = null,
)

/**
 * Delivery-confirmed recorder for Primed bursts. A timeout deliberately means only that no
 * correction was observed; it is never promoted to proof that the server accepted the movement.
 */
@Suppress("TooManyFunctions")
internal class SpearKillHighSpeedResearchRuntime(
    private val outputDirectory: Path,
    private val correctionObservationTicks: Int = DEFAULT_CORRECTION_OBSERVATION_TICKS,
) : AutoCloseable {

    private val activeBursts = linkedMapOf<String, ActiveSpearKillHighSpeedResearchBurst>()
    private var writer: SpearKillHighSpeedResearchJsonlWriter? = null
    private var loggingDisabled = false

    val pendingTargetEntityIds: Set<Int>
        get() = activeBursts.values.mapNotNullTo(linkedSetOf()) { it.start.target?.entityId }

    fun begin(start: SpearKillHighSpeedResearchBurstStart): String? {
        if (loggingDisabled || ensureWriter() == null) return null
        val id = UUID.randomUUID().toString()
        activeBursts[id] = ActiveSpearKillHighSpeedResearchBurst(
            id = id,
            start = start,
            startedAtEpochMs = System.currentTimeMillis(),
            startedAtMonotonicNanos = System.nanoTime(),
        )
        return id
    }

    fun recordPrimingPacket(id: String?, delivered: Boolean, blinkQueued: Boolean) {
        val burst = id?.let(activeBursts::get) ?: return
        burst.primingPacketsSent++
        if (delivered) burst.primingPacketsDelivered++ else burst.deliveryFailed = true
        burst.blinkQueued = burst.blinkQueued || blinkQueued
    }

    fun recordNoFallPacket(id: String?, delivered: Boolean, blinkQueued: Boolean) {
        val burst = id?.let(activeBursts::get) ?: return
        burst.noFallPacketsSent++
        if (!delivered) burst.deliveryFailed = true
        burst.blinkQueued = burst.blinkQueued || blinkQueued
    }

    fun recordFinalPacket(
        id: String?,
        delivered: Boolean,
        blinkQueued: Boolean,
        currentTick: Int,
    ) {
        val burst = id?.let(activeBursts::get) ?: return
        burst.finalPacketSent = true
        burst.finalPacketDelivered = delivered
        burst.finalPacketTick = currentTick
        burst.deliveryFailed = burst.deliveryFailed || !delivered
        burst.blinkQueued = burst.blinkQueued || blinkQueued
    }

    fun recordDeliveryFailure(id: String?) {
        id?.let(activeBursts::get)?.deliveryFailed = true
    }

    fun recordTickEndSuppressed() {
        activeBursts.values.filter { it.finalPacketSent }.forEach { it.tickEndPacketsSuppressed++ }
    }

    fun recordTickEndBoundary() {
        activeBursts.values.filter { it.finalPacketSent }.forEach { it.tickEndBoundariesObserved++ }
    }

    fun recordDamageEvent(entityId: Int) {
        activeBursts.values.filter { it.start.target?.entityId == entityId }
            .forEach { it.damageEventObserved = true }
    }

    fun updateTarget(entityId: Int, health: Double, dead: Boolean) {
        activeBursts.values.filter { it.start.target?.entityId == entityId }.forEach {
            it.targetHealthAfter = health
            it.targetDeathObserved = it.targetDeathObserved || dead
        }
    }

    fun observeLocalPosition(position: Vec3) {
        if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) return
        activeBursts.values.filter { it.finalPacketSent }.forEach { it.observedLocalPosition = position }
    }

    fun recordCorrection(correctedPosition: Vec3, currentTick: Int) {
        val burst = activeBursts.values.lastOrNull {
            it.finalPacketSent && it.correction == null
        } ?: return
        val receivedAt = System.currentTimeMillis()
        burst.correction = SpearKillHighSpeedResearchCorrection(
            receivedAtEpochMs = receivedAt,
            distance = burst.start.destination.distanceTo(correctedPosition),
            latencyMs = (receivedAt - burst.startedAtEpochMs).coerceAtLeast(0L),
            latencyTicks = (currentTick - burst.start.clientTick).coerceAtLeast(0),
        )
    }

    fun tick(currentTick: Int) {
        val completed = activeBursts.values.filter { burst ->
            burst.deliveryFailed || burst.correction != null || burst.finalPacketTick?.let {
                currentTick - it >= correctionObservationTicks
            } == true
        }
        completed.forEach { complete(it, currentTick) }
    }

    fun failAll(currentTick: Int) {
        activeBursts.values.forEach { it.deliveryFailed = true }
        activeBursts.values.toList().forEach { complete(it, currentTick) }
    }

    override fun close() {
        activeBursts.values.toList().forEach { burst ->
            if (!burst.finalPacketDelivered && burst.correction == null) burst.deliveryFailed = true
            complete(burst, burst.finalPacketTick ?: burst.start.clientTick)
        }
        runCatching { writer?.close() }
        writer = null
    }

    private fun complete(burst: ActiveSpearKillHighSpeedResearchBurst, currentTick: Int) {
        if (activeBursts.remove(burst.id) == null) return
        val entry = buildEntry(burst, currentTick)
        if (runCatching { writer?.write(entry) }.isFailure) disableLogging()
    }

    private fun buildEntry(
        burst: ActiveSpearKillHighSpeedResearchBurst,
        currentTick: Int,
    ) = SpearKillHighSpeedResearchEntry(
        burstId = burst.id,
        timing = buildTiming(burst, currentTick),
        packetPlan = buildPacketPlan(burst),
        movement = buildMovement(burst),
        sourcePrediction = buildSourcePrediction(burst.start),
        delivery = buildDelivery(burst),
        correction = burst.correction,
        target = buildTargetEvidence(burst),
        outcome = when {
            burst.correction != null -> SpearKillHighSpeedResearchOutcome.CORRECTED
            burst.deliveryFailed -> SpearKillHighSpeedResearchOutcome.DELIVERY_FAILED
            else -> SpearKillHighSpeedResearchOutcome.NO_CORRECTION_OBSERVED
        },
    )

    private fun buildTiming(
        burst: ActiveSpearKillHighSpeedResearchBurst,
        currentTick: Int,
    ) = SpearKillHighSpeedResearchTiming(
        startedAtEpochMs = burst.startedAtEpochMs,
        completedAtEpochMs = System.currentTimeMillis(),
        startedAtMonotonicNanos = burst.startedAtMonotonicNanos,
        completedAtMonotonicNanos = System.nanoTime(),
        clientTick = burst.start.clientTick,
        completionTick = currentTick,
    )

    private fun buildPacketPlan(burst: ActiveSpearKillHighSpeedResearchBurst) =
        SpearKillHighSpeedResearchPacketPlan(
            primingPacketsRequested = burst.start.primingPacketsRequested,
            primingPacketsSent = burst.primingPacketsSent,
            primingPacketType = burst.start.primingPacketType,
            finalPacketType = burst.start.finalPacketType,
            noFallPacketsSent = burst.noFallPacketsSent,
            packetBudget = burst.start.packetBudget,
        )

    private fun buildMovement(burst: ActiveSpearKillHighSpeedResearchBurst): SpearKillHighSpeedResearchMovement {
        val start = burst.start
        return SpearKillHighSpeedResearchMovement(
            origin = start.origin.toResearchVector(),
            destination = start.destination.toResearchVector(),
            localPositionBefore = start.localPositionBefore.toResearchVector(),
            observedLocalPosition = burst.observedLocalPosition?.toResearchVector(),
            requestedDistance = start.origin.distanceTo(start.destination),
            observedLocalDisplacement = burst.observedLocalPosition?.distanceTo(start.localPositionBefore),
            targetSpeed = start.targetSpeed,
            currentSpeed = start.currentSpeed,
            acceleration = start.acceleration,
            deceleration = start.deceleration,
            routeStepLimit = start.routeStepLimit,
            expectedVelocity = start.expectedVelocity,
            elytraFlying = start.elytraFlying,
            onGround = start.onGround,
            horizontalCollision = start.horizontalCollision,
            corridorBlocked = start.corridorBlocked,
            destinationSpaceFree = start.destinationSpaceFree,
            terminalRaytraceClear = start.terminalRaytraceClear,
        )
    }

    private fun buildSourcePrediction(
        start: SpearKillHighSpeedResearchBurstStart,
    ): SpearKillHighSpeedResearchSourcePrediction {
        val expectedVelocitySquared = start.expectedVelocity * start.expectedVelocity
        return SpearKillHighSpeedResearchSourcePrediction(
            squaredDistanceThresholdPerPacket = start.squaredDistanceThresholdPerPacket,
            expectedVelocitySquared = expectedVelocitySquared,
            effectivePacketCount = start.effectivePacketCount,
            packetCountReset = start.packetCountReset,
            predictedMaximumDistance = kotlin.math.sqrt(
                expectedVelocitySquared + start.squaredDistanceThresholdPerPacket * start.effectivePacketCount,
            ),
            predictedAccepted = start.predictedAccepted,
        )
    }

    private fun buildDelivery(burst: ActiveSpearKillHighSpeedResearchBurst) =
        SpearKillHighSpeedResearchDelivery(
            primingPacketsDelivered = burst.primingPacketsDelivered,
            finalPacketDelivered = burst.finalPacketDelivered,
            blinkQueued = burst.blinkQueued,
            tickEndPacketsSuppressed = burst.tickEndPacketsSuppressed,
            tickEndBoundariesObserved = burst.tickEndBoundariesObserved,
        )

    private fun buildTargetEvidence(
        burst: ActiveSpearKillHighSpeedResearchBurst,
    ): SpearKillHighSpeedResearchTargetEvidence? = burst.start.target?.let { target ->
        val healthAfter = burst.targetHealthAfter ?: target.health
        SpearKillHighSpeedResearchTargetEvidence(
            entityId = target.entityId,
            name = target.name,
            healthBefore = target.health,
            healthAfter = healthAfter,
            observedHealthDelta = (target.health - healthAfter).coerceAtLeast(0.0),
            damageEventObserved = burst.damageEventObserved,
            damageEventAmount = null,
            deathObserved = burst.targetDeathObserved,
            estimatedKineticDamage = target.estimatedKineticDamage,
        )
    }

    private fun ensureWriter(): SpearKillHighSpeedResearchJsonlWriter? {
        writer?.let { return it }
        if (loggingDisabled) return null
        val baseName = LocalDateTime.now().format(FILE_NAME_FORMAT)
        return runCatching {
            SpearKillHighSpeedResearchJsonlWriter.create(outputDirectory, baseName)
        }.onSuccess { writer = it }.getOrElse {
            disableLogging()
            null
        }
    }

    private fun disableLogging() {
        loggingDisabled = true
        runCatching { writer?.close() }
        writer = null
        activeBursts.clear()
    }

    private companion object {
        const val DEFAULT_CORRECTION_OBSERVATION_TICKS = 40
        val FILE_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}

private fun Vec3.toResearchVector() = SpearKillHighSpeedResearchVector(x, y, z)
