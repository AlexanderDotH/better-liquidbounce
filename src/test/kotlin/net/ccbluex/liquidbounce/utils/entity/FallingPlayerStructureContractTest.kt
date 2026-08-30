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
package net.ccbluex.liquidbounce.utils.entity

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FallingPlayerStructureContractTest {

    @Test
    fun `falling player keeps its public facade and delegates motion physics`() {
        assertTrue("@file:JvmName(\"FallingPlayerKt\")" in facadeSource)
        assertTrue("@file:JvmMultifileClass" in facadeSource)
        assertTrue("class FallingPlayer(" in facadeSource)
        assertTrue("fun fromPlayer(player: LocalPlayer, movementYaw: Float = player.yRot): FallingPlayer" in facadeSource)
        assertTrue("fun findCollision(ticks: Int): CollisionResult?" in facadeSource)
        assertTrue("fun willStartTickInBlockBeforeCollision(" in facadeSource)
        assertTrue("class CollisionResult(" in facadeSource)
        assertTrue("private val motion = FallingPlayerMotion(" in facadeSource)
        assertFalse("TooManyFunctions" in facadeSource)
        assertFalse("TooManyFunctions" in motionSource)
    }

    @Test
    fun `simulation keeps movement collision and post collision ordering`() {
        facadeSource.function("private fun advanceSimulation").assertInOrder(
            "val intendedMovement = motion.calculateMovementForTick(rotationVec)",
            "collidePlayer(intendedMovement)",
            "lastResolvedMovement = resolvedMovement",
            "boundingBox = boundingBox.move(resolvedMovement)",
            "if (collidedDownwards)",
            "motion.finishTick(intendedMovement, resolvedMovement)",
        )
        motionSource.function("fun finishTick").assertInOrder(
            "applyCollisionResponse(intendedMovement, resolvedMovement)",
            "if (!player.isFallFlying)",
            "applyFreeFallForces()",
            "simulatedTicks++",
        )
    }

    private fun String.function(signature: String): String {
        val start = indexOf(signature)
        assertTrue(start >= 0, "$signature is missing")
        val bodyStart = indexOf('{', start)
        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return substring(start, index + 1)
            }
        }
        error("$signature has no complete body")
    }

    private fun String.assertInOrder(vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/entity")
        val facadeSource: String = Files.readString(SOURCE_ROOT.resolve("FallingPlayer.kt"))
        val motionSource: String = Files.readString(SOURCE_ROOT.resolve("FallingPlayerMotion.kt"))
    }
}
