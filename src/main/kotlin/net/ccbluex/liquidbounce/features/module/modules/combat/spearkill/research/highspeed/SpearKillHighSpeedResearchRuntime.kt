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
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
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

/**
 * Delivery-confirmed recorder for Primed bursts. A timeout deliberately means only that no
 * correction was observed; it is never promoted to proof that the server accepted the movement.
 */
internal class SpearKillHighSpeedResearchRuntime(
    outputDirectory: Path,
    private val correctionObservationTicks: Int = DEFAULT_CORRECTION_OBSERVATION_TICKS,
) : AutoCloseable {

    private val activeBursts = linkedMapOf<String, ActiveSpearKillHighSpeedResearchBurst>()
    private val evidenceStore = SpearKillHighSpeedResearchEvidenceStore(outputDirectory)

    val pendingTargetEntityIds: Set<Int>
        get() = activeBursts.values.mapNotNullTo(linkedSetOf()) { it.start.target?.entityId }

    fun begin(start: SpearKillHighSpeedResearchBurstStart): String? {
        if (!evidenceStore.ensureAvailable()) return null
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
        evidenceStore.close()
    }

    private fun complete(burst: ActiveSpearKillHighSpeedResearchBurst, currentTick: Int) {
        if (activeBursts.remove(burst.id) == null) return
        if (!evidenceStore.write(buildSpearKillHighSpeedResearchEntry(burst, currentTick))) {
            activeBursts.clear()
        }
    }

    private companion object {
        const val DEFAULT_CORRECTION_OBSERVATION_TICKS = 40
    }
}
