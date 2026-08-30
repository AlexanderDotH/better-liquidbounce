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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip


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
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MaceClipResearchJsonlWriterTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `writer reuses unique evidence files without overwriting`() {
        temporaryDirectory.resolve("session.jsonl").toFile().writeText("existing")

        val writer = MaceClipResearchJsonlWriter.create(temporaryDirectory, "session")
        writer.close()

        assertEquals("session_1.jsonl", writer.file.fileName.toString())
        assertEquals("existing", temporaryDirectory.resolve("session.jsonl").toFile().readText())
    }

    @Test
    fun `writer flushes appended evidence and ignores writes after close`() {
        val writer = MaceClipResearchJsonlWriter.create(temporaryDirectory, "session")

        writer.write(completeEntry("session-1"))
        assertEquals(1, writer.file.toFile().readLines().size)
        writer.write(completeEntry("session-2"))
        assertEquals(2, writer.file.toFile().readLines().size)

        writer.close()
        writer.close()
        writer.write(completeEntry("ignored"))

        val lines = writer.file.toFile().readLines()
        assertEquals(2, lines.size)
        val json = JsonParser.parseString(lines.first()).asJsonObject
        assertEquals(1, json["schemaVersion"].asInt)
        assertEquals("session-1", json["sessionId"].asString)
        assertEquals("POSITION_ROTATION", json["request"].asJsonObject["packetShape"].asString)
        assertEquals(1.0, json["positions"].asJsonObject["origin"].asJsonObject["x"].asDouble)
        assertTrue(json.has("target"))
        assertTrue(json["target"].isJsonNull)
    }

    @Test
    fun `writer rejects unsafe evidence file names with stable errors`() {
        val blank = assertThrows(IllegalArgumentException::class.java) {
            MaceClipResearchJsonlWriter.create(temporaryDirectory, " ")
        }
        val path = assertThrows(IllegalArgumentException::class.java) {
            MaceClipResearchJsonlWriter.create(temporaryDirectory, "nested/session")
        }

        assertEquals("Research session name must not be blank", blank.message)
        assertEquals("Research session name must not contain a path", path.message)
    }

    private fun completeEntry(sessionId: String) = MaceClipResearchEntry(
        sessionId = sessionId,
        profile = MaceClipResearchProfiles.PAPER_26_2_BUILD_112,
        request = MaceClipResearchProbeRequest.Move(
            distance = 10.0,
            primingPackets = 1,
            packetShape = MaceClipResearchPacketShape.POSITION_ROTATION,
            clearance = 5.0,
            phaseDelayTicks = 1,
            terminalHoldTicks = 2,
        ),
        timing = MaceClipResearchTiming(
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 1_050L,
            startedAtMonotonicNanos = 2_000L,
            completedAtMonotonicNanos = 2_050L,
            clientTick = 7,
            completionTick = 8,
        ),
        phases = emptyList(),
        packets = listOf(
            MaceClipResearchPacketEvidence(
                sequence = 0,
                phase = MaceClipResearchPhase.TRANSFER,
                tick = 8,
                shape = MaceClipResearchPacketShape.POSITION_ROTATION,
                position = MaceClipResearchPosition(4.0, 5.0, 6.0),
                onGround = false,
                delivery = MaceClipResearchPacketDelivery.DELIVERED,
            ),
        ),
        corrections = emptyList(),
        positions = MaceClipResearchPositions(
            origin = MaceClipResearchPosition(1.0, 2.0, 3.0),
            target = null,
            attackEndpoint = MaceClipResearchPosition(4.0, 5.0, 6.0),
            apex = MaceClipResearchPosition(4.0, 10.0, 6.0),
            localBefore = MaceClipResearchPosition(1.0, 2.0, 3.0),
            localAfter = MaceClipResearchPosition(1.0, 2.0, 3.0),
            lastAuthoritativeCorrection = null,
            observedLocalDisplacement = 0.0,
        ),
        delivery = MaceClipResearchDeliveryEvidence(
            packetBudget = 8,
            packetsSent = 1,
            packetsDelivered = 1,
            packetsQueued = 0,
            packetsCancelled = 0,
            exactReturnDelivered = true,
        ),
        strike = MaceClipResearchStrikeEvidence(attempts = 0, committedAttacks = 0),
        target = null,
        abortRequested = false,
        outcome = MaceClipResearchOutcome.NO_CORRECTION_OBSERVED,
    )
}
