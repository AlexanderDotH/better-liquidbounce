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

private data class ActiveMaceClipResearchPhase(
    val phase: MaceClipResearchPhase,
    val startedTick: Int,
    val startPosition: Vec3,
    var completedTick: Int? = null,
    var endPosition: Vec3? = null,
)

private data class ActiveMaceClipResearchSession(
    val id: String,
    val start: MaceClipResearchStart,
    val startedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    val phases: MutableList<ActiveMaceClipResearchPhase> = mutableListOf(),
    val packets: MutableList<MaceClipResearchPacketEvidence> = mutableListOf(),
    val corrections: MutableList<MaceClipResearchCorrectionEvidence> = mutableListOf(),
    var currentPhase: MaceClipResearchPhase? = null,
    var abortRequested: Boolean = false,
    var lastAuthoritativeCorrection: Vec3? = null,
    var targetHealthAfter: Double? = start.target?.health,
    var damageEventObserved: Boolean = false,
    var damageEventAmount: Double? = null,
    var deathObserved: Boolean = false,
    var strikeAttempts: Int = 0,
    var committedAttacks: Int = 0,
)

/**
 * One-probe evidence recorder. It deliberately records only observed facts and never promotes a
 * correction-free timeout to proof of server acceptance.
 */
@Suppress("TooManyFunctions")
internal class MaceClipResearchRuntime(
    private val outputDirectory: Path,
) : AutoCloseable {

    private var active: ActiveMaceClipResearchSession? = null
    private var writer: MaceClipResearchJsonlWriter? = null
    private var loggingDisabled = false

    fun begin(start: MaceClipResearchStart): MaceClipResearchBeginResult {
        if (active != null) return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.ACTIVE_PROBE)
        if (!start.request.isValid()) {
            return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.INVALID_REQUEST)
        }
        if (!start.isValid()) {
            return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.INVALID_START)
        }
        if (ensureWriter() == null) {
            return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.LOGGING_UNAVAILABLE)
        }

        val id = UUID.randomUUID().toString()
        active = ActiveMaceClipResearchSession(
            id = id,
            start = start,
            startedAtEpochMs = System.currentTimeMillis(),
            startedAtMonotonicNanos = System.nanoTime(),
        )
        return MaceClipResearchBeginResult.Started(id)
    }

    fun status(): MaceClipResearchStatus = active?.let { session ->
        MaceClipResearchStatus.Active(
            sessionId = session.id,
            probe = session.start.request.probe,
            phase = session.currentPhase,
            profileId = session.start.profile.id,
            abortRequested = session.abortRequested,
        )
    } ?: MaceClipResearchStatus.Idle

    fun requestAbort(): MaceClipResearchAbortResult {
        val session = active ?: return MaceClipResearchAbortResult.IDLE
        session.abortRequested = true
        return MaceClipResearchAbortResult.ABORT_REQUESTED
    }

    fun recordPhaseStarted(id: String?, phase: MaceClipResearchPhase, tick: Int, position: Vec3) {
        val session = session(id) ?: return
        if (!position.isFinite()) return
        if (session.phases.any { it.phase == phase }) return
        session.currentPhase = phase
        session.phases += ActiveMaceClipResearchPhase(phase, tick, position)
    }

    fun recordPhaseCompleted(id: String?, phase: MaceClipResearchPhase, tick: Int, position: Vec3) {
        val session = session(id) ?: return
        if (!position.isFinite()) return
        val activePhase = session.phases.lastOrNull { it.phase == phase && it.completedTick == null } ?: return
        activePhase.completedTick = tick
        activePhase.endPosition = position
    }

    @Suppress("LongParameterList")
    fun recordPacket(
        id: String?,
        phase: MaceClipResearchPhase,
        sequence: Int,
        tick: Int,
        position: Vec3,
        onGround: Boolean,
        delivery: MaceClipResearchPacketDelivery,
    ) {
        val session = session(id) ?: return
        if (!position.isFinite() || sequence < 0 || session.packets.size >= session.start.packetBudget) return
        if (session.packets.any { it.sequence == sequence }) return
        session.packets += MaceClipResearchPacketEvidence(
            sequence = sequence,
            phase = phase,
            tick = tick,
            shape = session.start.request.packetShape,
            position = position.toResearchPosition(),
            onGround = onGround,
            delivery = delivery,
        )
    }

    fun recordCorrection(
        id: String?,
        phase: MaceClipResearchPhase,
        tick: Int,
        expected: Vec3,
        actual: Vec3,
    ) {
        val session = session(id) ?: return
        if (!expected.isFinite() || !actual.isFinite()) return
        if (session.corrections.size >= MAXIMUM_CORRECTIONS) return
        session.corrections += MaceClipResearchCorrectionEvidence(
            phase = phase,
            tick = tick,
            receivedAtEpochMs = System.currentTimeMillis(),
            expected = expected.toResearchPosition(),
            actual = actual.toResearchPosition(),
            distance = expected.distanceTo(actual),
        )
    }

    fun recordCorrectionAuthoritativePosition(id: String?, position: Vec3) {
        val session = session(id) ?: return
        if (position.isFinite()) session.lastAuthoritativeCorrection = position
    }

    fun recordDamage(id: String?, healthAfter: Double, amount: Double?) {
        val session = session(id) ?: return
        if (!healthAfter.isFinite() || healthAfter < 0.0) return
        if (amount != null && (!amount.isFinite() || amount < 0.0)) return
        session.targetHealthAfter = healthAfter
        session.damageEventObserved = true
        session.damageEventAmount = amount
    }

    fun recordDeath(id: String?) {
        session(id)?.deathObserved = true
    }

    fun recordStrikeAttempt(id: String?, committed: Boolean) {
        val session = session(id) ?: return
        if (session.start.request !is MaceClipResearchProbeRequest.Attack) return
        if (session.strikeAttempts < Int.MAX_VALUE) session.strikeAttempts++
        if (committed && session.committedAttacks < Int.MAX_VALUE) session.committedAttacks++
    }

    fun complete(
        id: String?,
        currentTick: Int,
        observedLocalPosition: Vec3,
        exactReturnDelivered: Boolean,
    ) {
        val session = session(id) ?: return
        if (!observedLocalPosition.isFinite()) return
        complete(session, currentTick, observedLocalPosition, exactReturnDelivered, forcedFailure = false)
    }

    override fun close() {
        active?.let { session ->
            complete(
                session = session,
                currentTick = session.start.clientTick,
                observedLocalPosition = session.start.localPositionBefore,
                exactReturnDelivered = false,
                forcedFailure = true,
            )
        }
        runCatching { writer?.close() }
        writer = null
    }

    private fun complete(
        session: ActiveMaceClipResearchSession,
        currentTick: Int,
        observedLocalPosition: Vec3,
        exactReturnDelivered: Boolean,
        forcedFailure: Boolean,
    ) {
        if (active !== session) return
        active = null
        val entry = buildEntry(session, currentTick, observedLocalPosition, exactReturnDelivered, forcedFailure)
        if (runCatching { writer?.write(entry) }.isFailure) disableLogging()
    }

    @Suppress("LongMethod")
    private fun buildEntry(
        session: ActiveMaceClipResearchSession,
        currentTick: Int,
        observedLocalPosition: Vec3,
        exactReturnDelivered: Boolean,
        forcedFailure: Boolean,
    ): MaceClipResearchEntry {
        val packetsDelivered = session.packets.count { it.delivery == MaceClipResearchPacketDelivery.DELIVERED }
        val packetsQueued = session.packets.count { it.delivery == MaceClipResearchPacketDelivery.QUEUED }
        val packetsCancelled = session.packets.count { it.delivery == MaceClipResearchPacketDelivery.CANCELLED }
        val deliveryFailed = forcedFailure || packetsQueued > 0 || packetsCancelled > 0 || !exactReturnDelivered
        return MaceClipResearchEntry(
            sessionId = session.id,
            profile = session.start.profile,
            request = session.start.request,
            timing = session.buildTiming(currentTick),
            phases = session.phases.map(ActiveMaceClipResearchPhase::toEvidence),
            packets = session.packets.toList(),
            corrections = session.corrections.toList(),
            positions = session.buildPositions(observedLocalPosition),
            delivery = MaceClipResearchDeliveryEvidence(
                packetBudget = session.start.packetBudget,
                packetsSent = session.packets.size,
                packetsDelivered = packetsDelivered,
                packetsQueued = packetsQueued,
                packetsCancelled = packetsCancelled,
                exactReturnDelivered = exactReturnDelivered,
            ),
            strike = MaceClipResearchStrikeEvidence(
                attempts = session.strikeAttempts,
                committedAttacks = session.committedAttacks,
            ),
            target = session.buildTargetEvidence(),
            abortRequested = session.abortRequested,
            outcome = when {
                session.corrections.isNotEmpty() -> MaceClipResearchOutcome.CORRECTED
                deliveryFailed -> MaceClipResearchOutcome.DELIVERY_FAILED
                session.abortRequested -> MaceClipResearchOutcome.ABORTED
                else -> MaceClipResearchOutcome.NO_CORRECTION_OBSERVED
            },
        )
    }

    private fun ActiveMaceClipResearchSession.buildTiming(currentTick: Int) = MaceClipResearchTiming(
        startedAtEpochMs = startedAtEpochMs,
        completedAtEpochMs = System.currentTimeMillis(),
        startedAtMonotonicNanos = startedAtMonotonicNanos,
        completedAtMonotonicNanos = System.nanoTime(),
        clientTick = start.clientTick,
        completionTick = currentTick,
    )

    private fun ActiveMaceClipResearchSession.buildPositions(localAfter: Vec3) = MaceClipResearchPositions(
        origin = start.origin.toResearchPosition(),
        target = start.targetPosition?.toResearchPosition(),
        attackEndpoint = start.attackEndpoint.toResearchPosition(),
        apex = start.apex.toResearchPosition(),
        localBefore = start.localPositionBefore.toResearchPosition(),
        localAfter = localAfter.toResearchPosition(),
        lastAuthoritativeCorrection = lastAuthoritativeCorrection?.toResearchPosition(),
        observedLocalDisplacement = start.localPositionBefore.distanceTo(localAfter),
    )

    private fun ActiveMaceClipResearchSession.buildTargetEvidence(): MaceClipResearchTargetEvidence? =
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

    private fun ensureWriter(): MaceClipResearchJsonlWriter? {
        writer?.let { return it }
        if (loggingDisabled) return null
        val baseName = "maceclip_" + LocalDateTime.now().format(FILE_NAME_FORMAT)
        return runCatching {
            MaceClipResearchJsonlWriter.create(outputDirectory, baseName)
        }.onSuccess { writer = it }.getOrElse {
            disableLogging()
            null
        }
    }

    private fun disableLogging() {
        loggingDisabled = true
        runCatching { writer?.close() }
        writer = null
        active = null
    }

    private fun session(id: String?): ActiveMaceClipResearchSession? = active?.takeIf { it.id == id }

    private companion object {
        const val MAXIMUM_CORRECTIONS = 64
        val FILE_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}

private fun MaceClipResearchStart.isValid(): Boolean =
    packetBudget in 1..MACE_CLIP_MAXIMUM_PACKET_BUDGET &&
        origin.isFinite() && attackEndpoint.isFinite() && apex.isFinite() && localPositionBefore.isFinite() &&
        targetPosition?.isFinite() != false &&
        target?.health?.let { it.isFinite() && it >= 0.0 } != false &&
        (request !is MaceClipResearchProbeRequest.Attack || targetPosition != null && target != null)

private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

private fun Vec3.toResearchPosition() = MaceClipResearchPosition(x, y, z)

private fun ActiveMaceClipResearchPhase.toEvidence() = MaceClipResearchPhaseEvidence(
    phase = phase,
    startedTick = startedTick,
    completedTick = completedTick,
    startPosition = startPosition.toResearchPosition(),
    endPosition = endPosition?.toResearchPosition(),
)
