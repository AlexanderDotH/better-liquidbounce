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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged.Companion.makeLookupTable
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachHitTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `Reach Hit is disabled by default and preserves the SuperHit settings`() {
        val hit = ReachHit(null)

        assertEquals("Hit", hit.name)
        assertFalse(hit.enabled)
        assertEquals(
            listOf("Enabled", "Mode", "MaxRange", "MinRange", "AttackRange", "Tracers"),
            hit.inner.filterNot { it.name == "TargetRendering" }.map { it.name },
        )
        assertEquals(100f, hit.inner.single { it.name == "MaxRange" }.get())
        assertEquals(3f, hit.inner.single { it.name == "MinRange" }.get())
        assertEquals(4.2f, hit.inner.single { it.name == "AttackRange" }.get())
        assertEquals(false, hit.inner.single { it.name == "Tracers" }.get())
    }

    @Test
    fun `Reach Hit exposes isolated mode settings and exact defaults`() {
        val configuration = ReachHitModeConfiguration(null)
        val mode = configuration.choice

        assertEquals("Packet", mode.activeMode.name)
        assertEquals(
            mapOf(
                "Packet" to listOf("StepSize"),
                "AStar" to listOf("MaxCost", "Diagonal"),
                "Adaptive" to listOf("InitialStep", "MinimumStep", "Retries", "VerifyTicks"),
                "Motion" to emptyList(),
                "Pulse" to listOf("StepSize", "Delay"),
                "Sentinel" to listOf("StayTicks"),
            ),
            mode.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
        assertEquals(10f, configuration.packet.stepSize)
        assertEquals(250, configuration.aStar.maxCost)
        assertFalse(configuration.aStar.diagonal)
        assertEquals(6f, configuration.adaptive.initialStep)
        assertEquals(0.75f, configuration.adaptive.minimumStep)
        assertEquals(3, configuration.adaptive.retries)
        assertEquals(2, configuration.adaptive.verifyTicks)
        assertEquals(10f, configuration.pulse.stepSize)
        assertEquals(1, configuration.pulse.delay)
        assertEquals(2, configuration.sentinel.stayTicks)

        val serializedMode = fileGson.toJsonTree(mode, ModeValueGroup::class.java).asJsonObject
        val choices = serializedMode["choices"].asJsonObject

        assertEquals(listOf("StepSize"), choices.valueNames("Packet"))
        assertEquals(listOf("MaxCost", "Diagonal"), choices.valueNames("AStar"))
        assertEquals(emptyList<String>(), choices.valueNames("Motion"))
    }

    @Test
    fun `legacy SuperHit values migrate into their Reach Hit modes`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "Hit",
              "value": [
                { "name": "Mode", "value": "AStar" },
                { "name": "StepSize", "value": 7.5 },
                { "name": "AStarMaxCost", "value": 321 },
                { "name": "AStarDiagonal", "value": true },
                { "name": "AdaptiveInitialStep", "value": 5.5 },
                { "name": "AdaptiveMinimumStep", "value": 0.5 },
                { "name": "AdaptiveRetries", "value": 4 },
                { "name": "AdaptiveVerifyTicks", "value": 3 },
                { "name": "PulseDelay", "value": 2 },
                { "name": "SentinelStayTicks", "value": 6 }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacyReachHitConfig(legacy)

        val values = legacy["value"].asJsonArray.map { it.asJsonObject }
        val mode = values.single { it["name"].asString == "Mode" }
        val choices = mode["choices"].asJsonObject

        assertEquals("AStar", mode["active"].asString)
        assertEquals(7.5f, choices.setting("Packet", "StepSize").asFloat)
        assertEquals(7.5f, choices.setting("Pulse", "StepSize").asFloat)
        assertEquals(321, choices.setting("AStar", "MaxCost").asInt)
        assertTrue(choices.setting("AStar", "Diagonal").asBoolean)
        assertEquals(5.5f, choices.setting("Adaptive", "InitialStep").asFloat)
        assertEquals(0.5f, choices.setting("Adaptive", "MinimumStep").asFloat)
        assertEquals(4, choices.setting("Adaptive", "Retries").asInt)
        assertEquals(3, choices.setting("Adaptive", "VerifyTicks").asInt)
        assertEquals(2, choices.setting("Pulse", "Delay").asInt)
        assertEquals(6, choices.setting("Sentinel", "StayTicks").asInt)
        assertEquals(listOf("Mode"), values.map { it["name"].asString })
    }

    @Test
    fun `legacy SuperHit mode aliases migrate without case-insensitive collisions`() {
        val aliases = mapOf(
            "Direct" to "Packet",
            "SinglePacket" to "Packet",
            "Cubecraft" to "Sentinel",
            "CubeCraft" to "Sentinel",
            "Cube Craft" to "Sentinel",
        )

        for ((alias, expected) in aliases) {
            val legacy = legacyModeConfig(alias)

            migrateLegacyReachHitConfig(legacy)

            assertEquals(expected, legacyMode(legacy)["active"].asString)
        }
    }

    @Test
    fun `changing configured mode retains captured execution travel mode`() {
        val executionMode = ReachHitExecutionMode()
        val configuredMode = ReachHitModeConfiguration(null).choice

        try {
            configuredMode.setByString("Packet")
            assertEquals(ReachHitMode.PACKET, executionMode.capture(configuredMode.activeMode.travelMode))

            configuredMode.setByString("Motion")
            assertEquals(ReachHitMode.MOTION, configuredMode.activeMode.travelMode)
            assertEquals(ReachHitMode.PACKET, executionMode.current)
        } finally {
            executionMode.clear()
            configuredMode.restore()
        }

        assertNull(executionMode.current)
    }

    @Test
    fun `sentinel mode accepts existing cubecraft configs`() {
        val modes = ReachHitMode.entries
        val lookup = modes.makeLookupTable()

        assertEquals(
            listOf("Packet", "AStar", "Adaptive", "Motion", "Pulse", "Sentinel"),
            modes.map { it.tag },
        )
        assertEquals(ReachHitMode.PACKET, lookup["Direct"])
        assertEquals(ReachHitMode.PACKET, lookup["SinglePacket"])
        listOf("Cubecraft", "CubeCraft", "Cube Craft").forEach { savedName ->
            assertEquals(ReachHitMode.SENTINEL, lookup[savedName])
        }
    }

    @Test
    fun `mode choice accepts every compatibility alias`() {
        val mode = ReachHitModeConfiguration(null).choice

        try {
            mapOf(
                "Direct" to "Packet",
                "SinglePacket" to "Packet",
                "Cubecraft" to "Sentinel",
                "CubeCraft" to "Sentinel",
                "Cube Craft" to "Sentinel",
            ).forEach { (alias, expected) ->
                mode.setByString(alias)
                assertEquals(expected, mode.activeMode.name)
            }
        } finally {
            mode.restore()
        }
    }

    @Test
    fun `packet modes expose distinct travel profiles`() {
        val origin = Vec3.ZERO
        val destination = Vec3(10.0, 0.0, 0.0)
        val packetPath = buildReachHitTravelPath(
            ReachHitMode.PACKET, origin, destination, stepSize = 4.0,
        )

        assertEquals(3, packetPath.size)
        assertVec3Equals(Vec3(10.0 / 3.0, 0.0, 0.0), packetPath[0], 1e-9)
        assertVec3Equals(Vec3(20.0 / 3.0, 0.0, 0.0), packetPath[1], 1e-9)
        assertVec3Equals(destination, packetPath[2], 1e-9)
        assertEquals(
            packetPath,
            buildReachHitTravelPath(ReachHitMode.PULSE, origin, destination, stepSize = 4.0),
        )
        assertEquals(
            packetPath,
            buildReachHitTravelPath(ReachHitMode.ADAPTIVE, origin, destination, stepSize = 4.0),
        )
        assertTrue(buildReachHitTravelPath(ReachHitMode.A_STAR, origin, destination, stepSize = 4.0).isEmpty())
        assertTrue(buildReachHitTravelPath(ReachHitMode.MOTION, origin, destination, stepSize = 4.0).isEmpty())
        assertTrue(buildReachHitTravelPath(ReachHitMode.SENTINEL, origin, destination, stepSize = 4.0).isEmpty())
    }

    @Test
    fun `packet travel classification excludes real movement modes`() {
        assertTrue(ReachHitMode.PACKET.usesPacketTravel)
        assertTrue(ReachHitMode.A_STAR.usesPacketTravel)
        assertTrue(ReachHitMode.ADAPTIVE.usesPacketTravel)
        assertTrue(ReachHitMode.PULSE.usesPacketTravel)
        assertFalse(ReachHitMode.MOTION.usesPacketTravel)
        assertFalse(ReachHitMode.SENTINEL.usesPacketTravel)
    }

    @Test
    fun `adaptive mode halves rejected steps down to its configured minimum`() {
        assertEquals(
            listOf(6.0, 3.0, 1.5, 0.75),
            calculateReachHitAdaptiveStepSizes(initialStep = 6.0, minimumStep = 0.75, retries = 3),
        )
    }

    @Test
    fun `adaptive mode attacks only after the server accepts a smaller step`() = runTest {
        val events = mutableListOf<String>()

        val success = executeAdaptiveReachHit(
            stepSizes = listOf(6.0, 3.0, 1.5),
            attempt = { step ->
                events += "attempt:$step"
                step <= 1.5
            },
            onAccepted = { step ->
                events += "attack:$step"
                true
            },
            onExhausted = { events += "recover" },
        )

        assertTrue(success)
        assertEquals(listOf("attempt:6.0", "attempt:3.0", "attempt:1.5", "attack:1.5"), events)
    }

    @Test
    fun `adaptive mode recovers without attacking after every step is rejected`() = runTest {
        val events = mutableListOf<String>()

        val success = executeAdaptiveReachHit(
            stepSizes = listOf(6.0, 3.0),
            attempt = { step ->
                events += "attempt:$step"
                false
            },
            onAccepted = { error("rejected attempts must not attack") },
            onExhausted = { events += "recover" },
        )

        assertFalse(success)
        assertEquals(listOf("attempt:6.0", "attempt:3.0", "recover"), events)
    }

    @Test
    fun `AStar return follows the discovered route back to the exact origin`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val outward = listOf(
            Vec3(1.5, 64.0, 0.5),
            Vec3(2.5, 64.0, 0.5),
            Vec3(3.5, 64.0, 0.5),
        )

        assertEquals(
            listOf(outward[1], outward[0], origin),
            buildReachHitAStarReturnPath(origin, outward),
        )
    }

    @Test
    fun `modern combat permits a charged Reach Hit attack`() {
        assertFalse(isReachHitAttackReady(usesAttackCooldown = true, attackStrength = 0.5f))
        assertTrue(isReachHitAttackReady(usesAttackCooldown = true, attackStrength = 1.0f))
        assertTrue(isReachHitAttackReady(usesAttackCooldown = false, attackStrength = 0.0f))
    }

    @Test
    fun `tracer is hidden by default and only renders with a target`() {
        assertFalse(shouldRenderReachHitTracer(tracersEnabled = false, hasTarget = true))
        assertFalse(shouldRenderReachHitTracer(tracersEnabled = true, hasTarget = false))
        assertTrue(shouldRenderReachHitTracer(tracersEnabled = true, hasTarget = true))
    }

    @Test
    fun `Reach Hit target range keeps its minimum exclusive and maximum inclusive`() {
        assertFalse(isWithinReachHitTargetRange(distanceSquared = 9.0, minRange = 3f, maxRange = 100f))
        assertTrue(isWithinReachHitTargetRange(distanceSquared = 9.01, minRange = 3f, maxRange = 100f))
        assertTrue(isWithinReachHitTargetRange(distanceSquared = 10_000.0, minRange = 3f, maxRange = 100f))
        assertFalse(isWithinReachHitTargetRange(distanceSquared = 10_000.01, minRange = 3f, maxRange = 100f))
    }

    @Test
    fun `automatic Reach Hit failures back off without delaying a different target`() {
        val retryGate = ReachHitAutomaticRetryGate(retryDelayTicks = 10)

        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 100))
        retryGate.recordFailure(targetId = 7, currentTick = 100)
        assertFalse(retryGate.canAttempt(targetId = 7, currentTick = 109))
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 110))
        assertTrue(retryGate.canAttempt(targetId = 8, currentTick = 101))
    }

    @Test
    fun `automatic Reach Hit retry gate resets after success or explicit clear`() {
        val retryGate = ReachHitAutomaticRetryGate(retryDelayTicks = 10)

        retryGate.recordFailure(targetId = 7, currentTick = 100)
        retryGate.recordSuccess()
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 101))

        retryGate.recordFailure(targetId = 7, currentTick = 102)
        retryGate.clear()
        assertTrue(retryGate.canAttempt(targetId = 7, currentTick = 103))
    }

    @Test
    fun `sentinel destination clears the target hitbox toward the player`() {
        assertVec3Equals(
            Vec3(9.3, 64.0, 0.0),
            calculateReachHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 0.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )

        assertVec3Equals(
            Vec3(9.3, 64.0, 9.3),
            calculateReachHitDestination(
                origin = Vec3.ZERO,
                targetPosition = Vec3(10.0, 64.0, 10.0),
                playerWidth = 0.6,
                targetWidth = 0.6,
            ),
            1e-9,
        )
    }

    @Test
    fun `sentinel attacks during a short stay and returns to its origin`() = runTest {
        val origin = Vec3(1.0, 64.0, 2.0)
        val target = Vec3(10.0, 64.0, 20.0)
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = origin,
            destination = target,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == target) "forward" else "return"
                true
            },
            shouldRecover = { true },
            synchronizeRotation = { events += "rotate" },
            attack = {
                events += "attack"
                true
            },
            wait = { ticks -> events += "wait:$ticks" },
        )

        assertTrue(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("forward", "rotate", "attack", "wait:2", "return"), events)
    }

    @Test
    fun `sentinel does not attack or return when ClickTP rejects without displacement`() = runTest {
        var attacked = false
        var teleportCalls = 0

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = {
                teleportCalls++
                false
            },
            shouldRecover = { false },
            synchronizeRotation = { error("rotation must not be synchronized") },
            attack = {
                attacked = true
                true
            },
            wait = { error("rejected teleport must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertFalse(outcome.returned)
        assertFalse(attacked)
        assertEquals(1, teleportCalls)
    }

    @Test
    fun `sentinel recovers to origin when an unreliable forward teleport displaces the player`() = runTest {
        val origin = Vec3(1.0, 0.0, 0.0)
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = origin,
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == Vec3.ZERO) "failed-forward" else "recover"
                destination == origin
            },
            shouldRecover = { true },
            synchronizeRotation = { error("rotation must not be synchronized") },
            attack = { error("attack must not run") },
            wait = { error("failed teleport must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("failed-forward", "recover"), events)
    }

    @Test
    fun `sentinel returns immediately when the post-teleport attack is rejected`() = runTest {
        val events = mutableListOf<String>()

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 2,
            teleport = { destination ->
                events += if (destination == Vec3.ZERO) "forward" else "return"
                true
            },
            shouldRecover = { true },
            synchronizeRotation = { events += "rotate" },
            attack = {
                events += "rejected"
                false
            },
            wait = { error("rejected attack must not dwell") },
        )

        assertFalse(outcome.attacked)
        assertTrue(outcome.returned)
        assertEquals(listOf("forward", "rotate", "rejected", "return"), events)
    }

    @Test
    fun `sentinel preserves attack success when its return teleport fails`() = runTest {
        var teleportCalls = 0

        val outcome = executeRoundTripReachHit(
            origin = Vec3(1.0, 0.0, 0.0),
            destination = Vec3.ZERO,
            stayTicks = 0,
            teleport = {
                teleportCalls++
                teleportCalls == 1
            },
            shouldRecover = { true },
            synchronizeRotation = {},
            attack = { true },
            wait = { error("zero stay ticks must not wait") },
        )

        assertTrue(outcome.attacked)
        assertFalse(outcome.returned)
        assertEquals(2, teleportCalls)
    }
}

private fun JsonObject.valueNames(choice: String): List<String> = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { it.asJsonObject["name"].asString }

private fun JsonObject.setting(choice: String, setting: String) = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == setting }["value"]

private fun legacyModeConfig(mode: String): JsonObject = JsonParser.parseString(
    """{ "name": "Hit", "value": [{ "name": "Mode", "value": "$mode" }] }""",
).asJsonObject

private fun legacyMode(config: JsonObject): JsonObject = config["value"].asJsonArray
    .map { it.asJsonObject }
    .single { it["name"].asString == "Mode" }
