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
import net.minecraft.world.phys.Vec3
import java.nio.file.Path
import java.util.UUID

/**
 * One-probe evidence recorder. It deliberately records only observed facts and never promotes a
 * correction-free timeout to proof of server acceptance.
 */
internal class MaceClipResearchRuntime(
    outputDirectory: Path,
) : AutoCloseable {

    private var active: ActiveMaceClipResearchSession? = null
    private val evidenceStore = MaceClipResearchEvidenceStore(outputDirectory)

    fun begin(start: MaceClipResearchStart): MaceClipResearchBeginResult {
        if (active != null) return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.ACTIVE_PROBE)
        if (!start.request.isValid()) {
            return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.INVALID_REQUEST)
        }
        if (!start.isValid()) {
            return MaceClipResearchBeginResult.Rejected(MaceClipResearchBeginRejection.INVALID_START)
        }
        if (!evidenceStore.ensureAvailable()) {
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
        if (session.corrections.size >= MACE_CLIP_MAXIMUM_CORRECTIONS) return
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
        evidenceStore.close()
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
        evidenceStore.write(
            buildMaceClipResearchEntry(
                session,
                currentTick,
                observedLocalPosition,
                exactReturnDelivered,
                forcedFailure,
            ),
        )
    }

    private fun session(id: String?): ActiveMaceClipResearchSession? = active?.takeIf { it.id == id }

}

private const val MACE_CLIP_MAXIMUM_CORRECTIONS = 64

private fun MaceClipResearchStart.isValid(): Boolean =
    packetBudget in 1..MACE_CLIP_MAXIMUM_PACKET_BUDGET &&
        origin.isFinite() && attackEndpoint.isFinite() && apex.isFinite() && localPositionBefore.isFinite() &&
        targetPosition?.isFinite() != false &&
        target?.health?.let { it.isFinite() && it >= 0.0 } != false &&
        (request !is MaceClipResearchProbeRequest.Attack || targetPosition != null && target != null)

private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

private fun Vec3.toResearchPosition() = MaceClipResearchPosition(x, y, z)
