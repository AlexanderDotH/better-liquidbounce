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
package net.ccbluex.liquidbounce.utils.aiming.projectiles

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CydhranianProjectileAngleCalculatorContractTest {

    @Test
    fun `target center offset remains half width half height and half width`() {
        assertTargetCenterOffset(EntityDimensions.scalable(2f, 4f), Vec3(1.0, 2.0, 1.0))
        assertTargetCenterOffset(EntityDimensions.scalable(3f, 1f), Vec3(1.5, 0.5, 1.5))
    }

    @Test
    fun `prediction retains its ballistic debug hitpoint and fallback order`() {
        val body = functionBody(source, "predictArrowDirection")
        assertEquals(2, Regex("""\?:\s*return null""").findAll(body).count())
        assertInOrder(
            body,
            "val defaultBoxOffset = getDefaultBoxOffset(targetDimensions)",
            "val ticksUntilImpact = calculatePossibleTravelTimeToTarget(",
            "val entityPositionOnImpact = positionFunction.getPositionInTicks(ticksUntilImpact)",
            "val finalDirection = getDirectionByTime(",
            "val directionOnImpact = getVelocityOnImpact(",
            "publishInboundDirection(entityPositionOnImpact, directionOnImpact)",
            "val finalTargetPos = ProjectileTargetPointFinder.findHittablePosition(",
            ".inflate(trajectoryInfo.hitboxRadius)",
            "return getDirectionByTime(trajectoryInfo, finalTargetPos, playerHeadPosition, round(ticksUntilImpact))",
        )

        assertInOrder(
            functionBody(source, "publishInboundDirection"),
            "DebugGeometrySink.publish(",
            "ProjectileAimingDebugOwner, \"inboundDirection\"",
            "DebuggedLineSegment(",
            "impactPosition.add(directionOnImpact.withLength(2.0))",
            "0xFF0000FF.toInt()",
        )
    }

    @Test
    fun `calculator retains its public JVM signature`() {
        val method = CydhranianProjectileAngleCalculator::class.java.getDeclaredMethod(
            "calculateAngleFor",
            TrajectoryInfo::class.java,
            Vec3::class.java,
            PositionExtrapolation::class.java,
            EntityDimensions::class.java,
        )
        assertEquals(Rotation::class.java, method.returnType)
    }

    private fun assertTargetCenterOffset(dimensions: EntityDimensions, expected: Vec3) {
        val method = CydhranianProjectileAngleCalculator::class.java.getDeclaredMethod(
            "getDefaultBoxOffset",
            EntityDimensions::class.java,
        ).apply { isAccessible = true }
        val actual = method.invoke(CydhranianProjectileAngleCalculator, dimensions) as Vec3
        assertEquals(expected.x, actual.x)
        assertEquals(expected.y, actual.y)
        assertEquals(expected.z, actual.z)
    }

    private fun functionBody(source: String, functionName: String): String {
        val signature = Regex("""fun\s+${Regex.escape(functionName)}\s*\(""")
            .find(source)?.range?.first
            ?: error("Missing function $functionName")
        val openingBrace = source.indexOf('{', signature)
        require(openingBrace >= 0) { "Missing body for $functionName" }
        var depth = 0
        source.forEachIndexed { index, character ->
            if (index < openingBrace) return@forEachIndexed
            when (character) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(openingBrace, index + 1)
            }
        }
        error("Unclosed body for $functionName")
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val source: String = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/projectiles/" +
                    "CydhranianProjectileAngleCalculator.kt",
            ),
        )
    }
}
