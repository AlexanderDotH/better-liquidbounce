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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MaceClipResearchRuntimeTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `one active probe records phase delivery correction position damage and death evidence`() {
        val runtime = MaceClipResearchRuntime(temporaryDirectory)
        val started = assertInstanceOf(
            MaceClipResearchBeginResult.Started::class.java,
            runtime.begin(start()),
        )
        assertInstanceOf(
            MaceClipResearchBeginResult.Rejected::class.java,
            runtime.begin(start()),
        )

        runtime.recordPhaseStarted(started.sessionId, MaceClipResearchPhase.PRIME, 41, Vec3(0.0, 64.0, 0.0))
        runtime.recordPacket(
            started.sessionId,
            MaceClipResearchPhase.PRIME,
            sequence = 0,
            tick = 41,
            position = Vec3(0.0, 64.0, 0.0),
            onGround = true,
            delivery = MaceClipResearchPacketDelivery.DELIVERED,
        )
        runtime.recordPhaseCompleted(started.sessionId, MaceClipResearchPhase.PRIME, 41, Vec3(0.0, 64.0, 0.0))
        runtime.recordCorrection(
            started.sessionId,
            MaceClipResearchPhase.ASCEND,
            tick = 42,
            expected = Vec3(0.0, 163.0, 0.0),
            actual = Vec3(0.0, 64.0, 0.0),
        )
        runtime.recordDamage(started.sessionId, healthAfter = 12.0, amount = 8.0)
        runtime.recordDeath(started.sessionId)
        runtime.recordStrikeAttempt(started.sessionId, committed = true)
        runtime.recordCorrectionAuthoritativePosition(started.sessionId, Vec3(0.0, 64.0, 0.0))
        runtime.complete(
            started.sessionId,
            currentTick = 45,
            observedLocalPosition = Vec3(0.0, 64.0, 0.0),
            exactReturnDelivered = true,
        )
        runtime.close()

        val json = singleEntry()
        assertEquals("paper-26.2-build-112-unvalidated", json["profile"].asJsonObject["id"].asString)
        assertEquals("UNVALIDATED", json["profile"].asJsonObject["validation"].asString)
        assertEquals("PRIME", json["phases"].asJsonArray[0].asJsonObject["phase"].asString)
        assertEquals("DELIVERED", json["packets"].asJsonArray[0].asJsonObject["delivery"].asString)
        assertEquals(99.0, json["corrections"].asJsonArray[0].asJsonObject["distance"].asDouble)
        assertEquals(8.0, json["target"].asJsonObject["observedHealthDelta"].asDouble)
        assertTrue(json["target"].asJsonObject["deathObserved"].asBoolean)
        assertEquals(1, json["strike"].asJsonObject["attempts"].asInt)
        assertEquals(1, json["strike"].asJsonObject["committedAttacks"].asInt)
        assertTrue(json["positions"].asJsonObject.has("lastAuthoritativeCorrection"))
        assertFalse(json["positions"].asJsonObject.has("authoritativeAfter"))
        assertTrue(json["delivery"].asJsonObject["exactReturnDelivered"].asBoolean)
        assertEquals("CORRECTED", json["outcome"].asString)
        assertFalse(json.has("accepted"))
    }

    @Test
    fun `delivered probe without correction remains unproven`() {
        val runtime = MaceClipResearchRuntime(temporaryDirectory)
        val id = (runtime.begin(start()) as MaceClipResearchBeginResult.Started).sessionId
        runtime.recordPacket(
            id,
            MaceClipResearchPhase.ASCEND,
            sequence = 0,
            tick = 41,
            position = Vec3(0.0, 163.0, 0.0),
            onGround = false,
            delivery = MaceClipResearchPacketDelivery.DELIVERED,
        )

        runtime.complete(id, 45, Vec3(0.0, 64.0, 0.0), exactReturnDelivered = true)
        runtime.close()

        assertEquals("NO_CORRECTION_OBSERVED", singleEntry()["outcome"].asString)
    }

    @Test
    fun `queued packet makes delivery fail and abort remains a recovery request`() {
        val runtime = MaceClipResearchRuntime(temporaryDirectory)
        val id = (runtime.begin(start()) as MaceClipResearchBeginResult.Started).sessionId

        assertEquals(MaceClipResearchAbortResult.ABORT_REQUESTED, runtime.requestAbort())
        assertTrue((runtime.status() as MaceClipResearchStatus.Active).abortRequested)
        runtime.recordPacket(
            id,
            MaceClipResearchPhase.TRANSFER,
            sequence = 0,
            tick = 42,
            position = Vec3(10.0, 163.0, 0.0),
            onGround = false,
            delivery = MaceClipResearchPacketDelivery.QUEUED,
        )
        runtime.complete(id, 43, Vec3(0.0, 64.0, 0.0), exactReturnDelivered = false)
        runtime.close()

        val json = singleEntry()
        assertEquals("DELIVERY_FAILED", json["outcome"].asString)
        assertTrue(json["abortRequested"].asBoolean)
        assertEquals(1, json["delivery"].asJsonObject["packetsQueued"].asInt)
        assertFalse(json["delivery"].asJsonObject["exactReturnDelivered"].asBoolean)
    }

    @Test
    fun `invalid request or targetless attack is rejected before evidence is opened`() {
        val runtime = MaceClipResearchRuntime(temporaryDirectory)
        val invalid = start().copy(
            request = MaceClipResearchProbeRequest.Move(
                distance = Double.NaN,
                primingPackets = 9,
                packetShape = MaceClipResearchPacketShape.POSITION,
                clearance = 99.0,
                phaseDelayTicks = 1,
                terminalHoldTicks = 2,
            ),
        )

        val rejected = assertInstanceOf(MaceClipResearchBeginResult.Rejected::class.java, runtime.begin(invalid))
        val targetlessAttack = assertInstanceOf(
            MaceClipResearchBeginResult.Rejected::class.java,
            runtime.begin(start().copy(targetPosition = null, target = null)),
        )

        assertEquals(MaceClipResearchBeginRejection.INVALID_REQUEST, rejected.reason)
        assertEquals(MaceClipResearchBeginRejection.INVALID_START, targetlessAttack.reason)
        val entries = if (Files.exists(temporaryDirectory)) {
            Files.list(temporaryDirectory).use { it.count() }
        } else {
            0L
        }
        assertEquals(0L, entries)
    }

    private fun start() = MaceClipResearchStart(
        clientTick = 40,
        request = MaceClipResearchProbeRequest.Attack(
            primingPackets = 9,
            packetShape = MaceClipResearchPacketShape.POSITION,
            clearance = 99.0,
            phaseDelayTicks = 1,
            terminalHoldTicks = 2,
        ),
        profile = MaceClipResearchProfiles.PAPER_26_2_BUILD_112,
        packetBudget = 64,
        origin = Vec3(0.0, 64.0, 0.0),
        targetPosition = Vec3(10.0, 64.0, 0.0),
        attackEndpoint = Vec3(7.0, 64.0, 0.0),
        apex = Vec3(0.0, 163.0, 0.0),
        localPositionBefore = Vec3(0.0, 64.0, 0.0),
        target = MaceClipResearchTargetStart(7, "Target", 20.0),
    )

    private fun singleEntry(): JsonObject {
        val file = Files.list(temporaryDirectory).use { files -> files.findFirst().orElseThrow() }
        return JsonParser.parseString(Files.readString(file).trim()).asJsonObject
    }
}
