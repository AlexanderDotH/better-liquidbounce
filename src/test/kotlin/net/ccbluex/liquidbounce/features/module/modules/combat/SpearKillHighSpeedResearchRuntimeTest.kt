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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SpearKillHighSpeedResearchRuntimeTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `delivered timeout remains no correction observed rather than accepted`() {
        val runtime = SpearKillHighSpeedResearchRuntime(temporaryDirectory, correctionObservationTicks = 1)
        val id = requireNotNull(runtime.begin(start()))
        runtime.recordPrimingPacket(id, delivered = true, blinkQueued = false)
        runtime.recordFinalPacket(id, delivered = true, blinkQueued = false, currentTick = 40)
        runtime.observeLocalPosition(Vec3(100.0, 64.0, 0.0))
        runtime.recordTickEndBoundary()
        runtime.tick(41)
        runtime.close()

        val json = singleEntry()
        assertEquals("NO_CORRECTION_OBSERVED", json["outcome"].asString)
        assertTrue(json["delivery"].asJsonObject["finalPacketDelivered"].asBoolean)
        assertEquals(1, json["delivery"].asJsonObject["tickEndBoundariesObserved"].asInt)
        assertEquals(100.0, json["movement"].asJsonObject["observedLocalDisplacement"].asDouble)
    }

    @Test
    fun `correction is recorded without turning it into a movement retry`() {
        val runtime = SpearKillHighSpeedResearchRuntime(temporaryDirectory)
        val id = requireNotNull(runtime.begin(start()))
        runtime.recordFinalPacket(id, delivered = true, blinkQueued = false, currentTick = 40)
        runtime.recordCorrection(Vec3(0.0, 64.0, 0.0), currentTick = 41)
        runtime.tick(41)
        runtime.close()

        val json = singleEntry()
        assertEquals("CORRECTED", json["outcome"].asString)
        assertEquals(100.0, json["correction"].asJsonObject["distance"].asDouble)
        assertFalse(json["delivery"].asJsonObject["blinkQueued"].asBoolean)
    }

    @Test
    fun `Blink queued priming fails delivery before a final packet`() {
        val runtime = SpearKillHighSpeedResearchRuntime(temporaryDirectory)
        val id = requireNotNull(runtime.begin(start()))
        runtime.recordPrimingPacket(id, delivered = false, blinkQueued = true)
        runtime.tick(40)
        runtime.close()

        val json = singleEntry()
        assertEquals("DELIVERY_FAILED", json["outcome"].asString)
        assertTrue(json["delivery"].asJsonObject["blinkQueued"].asBoolean)
        assertFalse(json["delivery"].asJsonObject["finalPacketDelivered"].asBoolean)
    }

    private fun start() = SpearKillHighSpeedResearchBurstStart(
        clientTick = 40,
        primingPacketsRequested = 4,
        primingPacketType = SpearKillHighSpeedResearchPacketType.POSITION,
        finalPacketType = SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
        packetBudget = 128,
        origin = Vec3(0.0, 64.0, 0.0),
        destination = Vec3(100.0, 64.0, 0.0),
        localPositionBefore = Vec3(0.0, 64.0, 0.0),
        targetSpeed = 100.0,
        currentSpeed = 0.0,
        acceleration = 100.0,
        deceleration = 100.0,
        routeStepLimit = 100.0,
        expectedVelocity = 0.0,
        elytraFlying = false,
        onGround = true,
        horizontalCollision = false,
        squaredDistanceThresholdPerPacket = 100.0,
        effectivePacketCount = 5,
        packetCountReset = false,
        predictedAccepted = false,
        corridorBlocked = true,
        destinationSpaceFree = true,
        terminalRaytraceClear = true,
        target = null,
    )

    private fun singleEntry(): JsonObject {
        val file = Files.list(temporaryDirectory).use { files -> files.findFirst().orElseThrow() }
        return JsonParser.parseString(Files.readString(file).trim()).asJsonObject
    }
}
