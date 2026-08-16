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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillNetworkOptimizerTest {

    @Test
    fun `healthy server keeps the configured safety floor without reducing speed`() {
        val optimizer = SpearKillNetworkOptimizer()

        val budget = optimizer.resolve(
            observation = SpearKillNetworkObservation(serverTps = 20.0, pingMillis = 0),
            settings = networkSettings(),
        )

        assertEquals(10.0, budget.maxSpeed, 1e-9)
        assertEquals(1, budget.stepWaitTicks)
        assertEquals(2, budget.damageEvidenceWindowTicks)
        assertFalse(budget.allowTerminalBurst)
    }

    @Test
    fun `five TPS and high ping pace packets and widen damage evidence`() {
        val optimizer = SpearKillNetworkOptimizer()

        val budget = optimizer.resolve(
            observation = SpearKillNetworkObservation(serverTps = 5.0, pingMillis = 180),
            settings = networkSettings(minimumStepWaitTicks = 0),
        )

        assertEquals(3, budget.stepWaitTicks)
        assertEquals(6, budget.damageEvidenceWindowTicks)
    }

    @Test
    fun `setback reduces speed adds cadence and blocks only the configured interval`() {
        val optimizer = SpearKillNetworkOptimizer()
        optimizer.recordSetback(currentTick = 100, backoffTicks = 40)

        val budget = optimizer.resolve(
            observation = SpearKillNetworkObservation(serverTps = 20.0, pingMillis = 0),
            settings = networkSettings(),
        )

        assertEquals(7.5, budget.maxSpeed, 1e-9)
        assertEquals(2, budget.stepWaitTicks)
        assertFalse(optimizer.canStartAttempt(currentTick = 139))
        assertTrue(optimizer.canStartAttempt(currentTick = 140))
    }

    @Test
    fun `a clean round trip removes one setback penalty`() {
        val optimizer = SpearKillNetworkOptimizer()
        optimizer.recordSetback(currentTick = 100, backoffTicks = 40)
        optimizer.recordSetback(currentTick = 110, backoffTicks = 40)

        optimizer.recordSuccessfulRoundTrip()

        val budget = optimizer.resolve(
            observation = SpearKillNetworkObservation(serverTps = 20.0, pingMillis = 0),
            settings = networkSettings(),
        )
        assertEquals(7.5, budget.maxSpeed, 1e-9)
        assertEquals(2, budget.stepWaitTicks)
    }

    @Test
    fun `network pacing removes only the same tick terminal burst metadata`() {
        val outbound = listOf(
            Vec3(0.0, -2.0, 0.0),
            Vec3(0.0, -2.0, 0.0),
            Vec3(0.0, -1.0, 0.0),
        )
        val route = SpearKillAStarPacketRoute(
            outboundMovements = outbound,
            roundTripMovements = outbound + outbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            terminalBurstSteps = 3,
        )

        val paced = paceSpearKillNetworkRoute(route)

        assertEquals(0, paced.terminalBurstSteps)
        assertEquals(outbound.size, paced.outboundTickCount)
        assertSame(route.outboundMovements, paced.outboundMovements)
        assertSame(route.roundTripMovements, paced.roundTripMovements)
        assertSame(paced, paceSpearKillNetworkRoute(paced))
    }

    private fun networkSettings(
        minimumStepWaitTicks: Int = 1,
    ) = SpearKillNetworkSettings(
        maxSpeed = 10.0,
        minimumStepWaitTicks = minimumStepWaitTicks,
        setbackBackoffTicks = 40,
    )
}
