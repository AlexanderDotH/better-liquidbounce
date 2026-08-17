/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SpearKillHighSpeedResearchJsonlWriterTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `research outcomes never claim server acceptance`() {
        assertEquals(
            listOf("CORRECTED", "DELIVERY_FAILED", "NO_CORRECTION_OBSERVED"),
            SpearKillHighSpeedResearchOutcome.entries.map { it.name },
        )
    }

    @Test
    fun `writer persists a complete probe result as one json line`() {
        val writer = SpearKillHighSpeedResearchJsonlWriter.create(temporaryDirectory, "session")
        val entry = completeEntry()

        writer.write(entry)
        writer.close()

        val lines = writer.file.toFile().readLines()
        assertEquals(1, lines.size)
        val json = JsonParser.parseString(lines.single()).asJsonObject
        assertEquals(2, json["schemaVersion"].asInt)
        assertEquals("burst-123", json["burstId"].asString)
        assertEquals(42, json["timing"].asJsonObject["clientTick"].asInt)
        assertEquals(9, json["packetPlan"].asJsonObject["primingPacketsRequested"].asInt)
        assertEquals("POSITION", json["packetPlan"].asJsonObject["primingPacketType"].asString)
        assertEquals("POSITION_ROTATION", json["packetPlan"].asJsonObject["finalPacketType"].asString)
        assertEquals(100.0, json["movement"].asJsonObject["requestedDistance"].asDouble)
        assertEquals(99.5, json["movement"].asJsonObject["observedLocalDisplacement"].asDouble)
        assertEquals(5.0, json["movement"].asJsonObject["acceleration"].asDouble)
        assertTrue(json["movement"].asJsonObject["corridorBlocked"].asBoolean)
        assertTrue(json["movement"].asJsonObject["elytraFlying"].asBoolean)
        assertTrue(json["sourcePrediction"].asJsonObject["packetCountReset"].asBoolean)
        assertTrue(json["delivery"].asJsonObject["blinkQueued"].asBoolean)
        assertEquals(124.8, json["correction"].asJsonObject["distance"].asDouble)
        assertEquals(7.5, json["target"].asJsonObject["observedHealthDelta"].asDouble)
        assertEquals("CORRECTED", json["outcome"].asString)
        assertTrue(lines.single().startsWith("{"))
        assertTrue(lines.single().endsWith("}"))
    }

    @Test
    fun `writer keeps nullable evidence fields explicit`() {
        val writer = SpearKillHighSpeedResearchJsonlWriter.create(temporaryDirectory, "move")
        val entry = completeEntry().copy(correction = null, target = null)

        writer.write(entry)
        writer.close()

        val json = JsonParser.parseString(writer.file.toFile().readText()).asJsonObject
        assertTrue(json.has("correction"))
        assertTrue(json["correction"].isJsonNull)
        assertTrue(json.has("target"))
        assertTrue(json["target"].isJsonNull)
    }

    @Test
    fun `writer creates a unique session file without overwriting evidence`() {
        temporaryDirectory.resolve("session.jsonl").toFile().writeText("existing")

        val writer = SpearKillHighSpeedResearchJsonlWriter.create(temporaryDirectory, "session")
        writer.close()

        assertEquals("session_1.jsonl", writer.file.fileName.toString())
        assertEquals("existing", temporaryDirectory.resolve("session.jsonl").toFile().readText())
    }

    @Suppress("LongMethod")
    private fun completeEntry() = SpearKillHighSpeedResearchEntry(
        burstId = "burst-123",
        timing = SpearKillHighSpeedResearchTiming(
            startedAtEpochMs = 1_000L,
            completedAtEpochMs = 1_050L,
            startedAtMonotonicNanos = 2_000L,
            completedAtMonotonicNanos = 2_050L,
            clientTick = 42,
            completionTick = 43,
        ),
        packetPlan = SpearKillHighSpeedResearchPacketPlan(
            primingPacketsRequested = 9,
            primingPacketsSent = 9,
            primingPacketType = SpearKillHighSpeedResearchPacketType.POSITION,
            finalPacketType = SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
            noFallPacketsSent = 1,
            packetBudget = 20,
        ),
        movement = SpearKillHighSpeedResearchMovement(
            origin = SpearKillHighSpeedResearchVector(1.0, 64.0, 2.0),
            destination = SpearKillHighSpeedResearchVector(101.0, 64.0, 2.0),
            localPositionBefore = SpearKillHighSpeedResearchVector(1.0, 64.0, 2.0),
            observedLocalPosition = SpearKillHighSpeedResearchVector(100.5, 64.0, 2.0),
            requestedDistance = 100.0,
            observedLocalDisplacement = 99.5,
            targetSpeed = 100.0,
            currentSpeed = 95.0,
            acceleration = 5.0,
            deceleration = 7.5,
            routeStepLimit = 10.0,
            expectedVelocity = 1.25,
            elytraFlying = true,
            onGround = false,
            horizontalCollision = true,
            corridorBlocked = true,
            destinationSpaceFree = true,
            terminalRaytraceClear = true,
        ),
        sourcePrediction = SpearKillHighSpeedResearchSourcePrediction(
            squaredDistanceThresholdPerPacket = 300.0,
            expectedVelocitySquared = 1.5625,
            effectivePacketCount = 1,
            packetCountReset = true,
            predictedMaximumDistance = 17.32,
            predictedAccepted = false,
        ),
        delivery = SpearKillHighSpeedResearchDelivery(
            primingPacketsDelivered = 9,
            finalPacketDelivered = false,
            blinkQueued = true,
            tickEndPacketsSuppressed = 1,
            tickEndBoundariesObserved = 1,
        ),
        correction = SpearKillHighSpeedResearchCorrection(
            receivedAtEpochMs = 1_075L,
            distance = 124.8,
            latencyMs = 25L,
            latencyTicks = 1,
        ),
        target = SpearKillHighSpeedResearchTargetEvidence(
            entityId = 7,
            name = "Target",
            healthBefore = 20.0,
            healthAfter = 12.5,
            observedHealthDelta = 7.5,
            damageEventObserved = true,
            damageEventAmount = null,
            deathObserved = false,
            estimatedKineticDamage = 8.0,
        ),
        outcome = SpearKillHighSpeedResearchOutcome.CORRECTED,
    )
}
