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
package net.ccbluex.liquidbounce.features.litematica.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LitematicaPrinterInteractionTest {

    @Test
    fun `only one new interaction starts per tick and the action delay is enforced`() {
        val fixture = readyFixture(actionDelayTicks = 3)

        val first = fixture.runtime.startInteraction(target("a"), PrinterInteractionKind.PLACE).accepted()
        val sameTick = fixture.runtime.startInteraction(target("b"), PrinterInteractionKind.PLACE).rejected()
        fixture.tick(2)
        val delayed = fixture.runtime.startInteraction(target("b"), PrinterInteractionKind.PLACE).rejected()
        fixture.tick(4)
        val next = fixture.runtime.startInteraction(target("b"), PrinterInteractionKind.PLACE).accepted()

        assertEquals(PrinterInteractionId(1), first.id)
        assertEquals(PrinterInteractionRejection.ALREADY_STARTED_THIS_TICK, sameTick)
        assertEquals(PrinterInteractionRejection.ACTION_DELAY, delayed)
        assertEquals(PrinterInteractionId(2), next.id)
    }

    @Test
    fun `different positions continue while multiple confirmations are pending`() {
        val fixture = readyFixture()

        val first = fixture.runtime.startInteraction(target("a"), PrinterInteractionKind.PLACE).accepted()
        fixture.clock.now = 500
        fixture.tick(2)
        val second = fixture.runtime.startInteraction(target("b"), PrinterInteractionKind.FLUID_PLACE).accepted()

        assertEquals(listOf(first, second), fixture.runtime.snapshot.pendingInteractions)
        assertEquals(setOf(target("a"), target("b")), fixture.runtime.snapshot.pendingTargets)

        fixture.clock.now = 1_999
        assertTrue(fixture.tick(3).timedOutInteractions.isEmpty())

        fixture.clock.now = 2_000
        assertEquals(listOf(first), fixture.tick(4).timedOutInteractions)
        assertEquals(listOf(second), fixture.runtime.snapshot.pendingInteractions)

        fixture.clock.now = 2_500
        assertEquals(listOf(second), fixture.tick(5).timedOutInteractions)
        assertTrue(fixture.runtime.snapshot.pendingInteractions.isEmpty())
    }

    @Test
    fun `a missing confirmation at the two second deadline is one confirmed retry failure`() {
        val fixture = readyFixture(retryLimit = 1)
        val pending = fixture.runtime.startInteraction(target("timeout"), PrinterInteractionKind.PLACE).accepted()
        fixture.clock.now = 2_000

        val tick = fixture.tick(2)

        assertEquals(listOf(pending), tick.timedOutInteractions)
        assertEquals(1, fixture.runtime.snapshot.failureCounts[pending.target])
        assertEquals(PrinterRuntimePhase.PAUSED, fixture.runtime.snapshot.phase)
        assertEquals(PrinterPauseReason.RETRY_LIMIT_REACHED, fixture.runtime.snapshot.pauseReason)
    }

    @Test
    fun `one owned Vanilla mining session is continued and excludes new work`() {
        val fixture = readyFixture()
        val mine = fixture.runtime.startInteraction(target("mine"), PrinterInteractionKind.BREAK).accepted()

        val nextTick = fixture.tick(2)
        val rejected = fixture.runtime.startInteraction(target("other"), PrinterInteractionKind.PLACE).rejected()

        assertEquals(PrinterMiningSession(mine.id, mine.target), fixture.runtime.snapshot.ownedMiningSession)
        assertEquals(fixture.runtime.snapshot.ownedMiningSession, nextTick.miningSessionToContinue)
        assertEquals(PrinterInteractionRejection.MINING_IN_PROGRESS, rejected)

        val confirmation = fixture.runtime.confirmInteraction(mine.id, PrinterInteractionOutcome.SUCCESS)
        assertTrue(confirmation.matched)
        assertNull(fixture.runtime.snapshot.ownedMiningSession)

        fixture.tick(3)
        assertEquals(
            target("other"),
            fixture.runtime.startInteraction(target("other"), PrinterInteractionKind.PLACE).accepted().target,
        )
    }

    @Test
    fun `the tenth confirmed failure pauses exactly on the configured retry limit`() {
        val fixture = readyFixture(retryLimit = 10)
        val failedTarget = target("stubborn")

        repeat(9) { index ->
            if (index > 0) fixture.tick((index + 1).toLong())
            val interaction = fixture.runtime.startInteraction(failedTarget, PrinterInteractionKind.PLACE).accepted()
            val result = fixture.runtime.confirmInteraction(interaction.id, PrinterInteractionOutcome.FAILURE)

            assertEquals(index + 1, result.failureCount)
            assertEquals(PrinterRuntimePhase.READY, fixture.runtime.snapshot.phase)
        }

        fixture.tick(10)
        val tenth = fixture.runtime.startInteraction(failedTarget, PrinterInteractionKind.PLACE).accepted()
        val result = fixture.runtime.confirmInteraction(tenth.id, PrinterInteractionOutcome.FAILURE)

        assertEquals(10, result.failureCount)
        assertTrue(result.paused)
        assertEquals(PrinterRuntimePhase.PAUSED, fixture.runtime.snapshot.phase)
        assertEquals(PrinterPauseReason.RETRY_LIMIT_REACHED, fixture.runtime.snapshot.pauseReason)
        assertEquals(failedTarget, fixture.runtime.snapshot.failedTarget)
    }

    @Test
    fun `unknown and duplicate confirmations never increment retries`() {
        val fixture = readyFixture()
        val pending = fixture.runtime.startInteraction(target("a"), PrinterInteractionKind.PLACE).accepted()

        assertFalse(
            fixture.runtime.confirmInteraction(PrinterInteractionId(99), PrinterInteractionOutcome.FAILURE).matched,
        )
        assertTrue(fixture.runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.FAILURE).matched)
        assertFalse(fixture.runtime.confirmInteraction(pending.id, PrinterInteractionOutcome.FAILURE).matched)
        assertEquals(1, fixture.runtime.snapshot.failureCounts[target("a")])
    }

    @Test
    fun `placement change clears pending mining and retry state without removing the provider`() {
        val fixture = readyFixture()
        val failed = fixture.runtime.startInteraction(target("failed"), PrinterInteractionKind.PLACE).accepted()
        fixture.runtime.confirmInteraction(failed.id, PrinterInteractionOutcome.FAILURE)
        fixture.tick(2)
        val mining = fixture.runtime.startInteraction(target("mine"), PrinterInteractionKind.BREAK).accepted()

        val cleanup = fixture.runtime.placementChanged()

        assertEquals(listOf(mining), cleanup.cancelledInteractions)
        assertEquals(PrinterMiningSession(mining.id, mining.target), cleanup.miningSessionToStop)
        assertFalse(cleanup.removePositionProvider)
        assertTrue(cleanup.clearOverlays)
        assertTrue(fixture.runtime.snapshot.failureCounts.isEmpty())
        assertTrue(fixture.runtime.snapshot.pendingInteractions.isEmpty())
    }

    @Test
    fun `world change clears runtime state and requests complete integration cleanup`() {
        val fixture = readyFixture()
        val pending = fixture.runtime.startInteraction(target("pending"), PrinterInteractionKind.PLACE).accepted()

        val cleanup = fixture.runtime.worldChanged()

        assertEquals(listOf(pending), cleanup.cancelledInteractions)
        assertTrue(cleanup.removePositionProvider)
        assertTrue(cleanup.clearOverlays)
        assertEquals(PrinterRuntimePhase.IDLE, fixture.runtime.snapshot.phase)
        assertTrue(fixture.runtime.snapshot.pendingInteractions.isEmpty())

        fixture.tick(2)
        assertEquals(PrinterRuntimePhase.READY, fixture.runtime.snapshot.phase)
    }

    @Test
    fun `manual reenable resets a retry pause and deterministically cleans owned work`() {
        val fixture = readyFixture(retryLimit = 1)
        val failed = fixture.runtime.startInteraction(target("failed"), PrinterInteractionKind.PLACE).accepted()
        fixture.runtime.confirmInteraction(failed.id, PrinterInteractionOutcome.FAILURE)
        assertEquals(PrinterRuntimePhase.PAUSED, fixture.runtime.snapshot.phase)

        val enabled = fixture.runtime.enable(currentPrinterToggle = true, currentEasyPlace = true)

        assertTrue(enabled.cleanup.removePositionProvider)
        assertTrue(enabled.cleanup.clearOverlays)
        assertTrue(fixture.runtime.snapshot.failureCounts.isEmpty())
        assertNull(fixture.runtime.snapshot.failedTarget)

        fixture.tick(2)
        assertEquals(PrinterRuntimePhase.READY, fixture.runtime.snapshot.phase)
    }

    @Test
    fun `disable cleanup preserves interaction order and is idempotent`() {
        val fixture = readyFixture()
        val first = fixture.runtime.startInteraction(target("a"), PrinterInteractionKind.PLACE).accepted()
        fixture.tick(2)
        val mining = fixture.runtime.startInteraction(target("b"), PrinterInteractionKind.BREAK).accepted()

        val cleanup = fixture.runtime.disable()
        val repeated = fixture.runtime.disable()

        assertEquals(listOf(first, mining), cleanup.cancelledInteractions)
        assertEquals(PrinterMiningSession(mining.id, mining.target), cleanup.miningSessionToStop)
        assertTrue(cleanup.removePositionProvider)
        assertTrue(cleanup.clearOverlays)
        assertTrue(repeated.cancelledInteractions.isEmpty())
        assertNull(repeated.miningSessionToStop)
        assertEquals(PrinterRuntimePhase.DISABLED, fixture.runtime.snapshot.phase)
    }

    private fun readyFixture(
        actionDelayTicks: Int = 1,
        retryLimit: Int = 10,
    ): Fixture {
        val clock = FakeTimeSource()
        val runtime = LitematicaPrinterRuntime<TestTarget>(clock)
        runtime.enable(currentPrinterToggle = true, currentEasyPlace = true)
        runtime.setActivationMode(PrinterActivationMode.CONTINUOUS)
        runtime.beginTick(PrinterTickInput(
            tick = 1,
            litematicaKeyActive = false,
            policy = PrinterRuntimePolicy(actionDelayTicks = actionDelayTicks, retryLimit = retryLimit),
        ))
        return Fixture(runtime, clock, actionDelayTicks, retryLimit)
    }

    private fun target(name: String) = TestTarget(name)

    private fun PrinterInteractionStart<TestTarget>.accepted(): PendingPrinterInteraction<TestTarget> {
        return (this as PrinterInteractionStart.Accepted).interaction
    }

    private fun PrinterInteractionStart<TestTarget>.rejected(): PrinterInteractionRejection {
        return (this as PrinterInteractionStart.Rejected).reason
    }

    private data class TestTarget(val name: String)

    private data class Fixture(
        val runtime: LitematicaPrinterRuntime<TestTarget>,
        val clock: FakeTimeSource,
        val actionDelayTicks: Int,
        val retryLimit: Int,
    ) {
        private var policy = PrinterRuntimePolicy(actionDelayTicks = actionDelayTicks, retryLimit = retryLimit)

        fun tick(tick: Long): PrinterTickResult<TestTarget> = runtime.beginTick(
            PrinterTickInput(tick = tick, litematicaKeyActive = false, policy = policy),
        )
    }

    private class FakeTimeSource(var now: Long = 0L) : PrinterTimeSource {
        override fun nowMillis(): Long = now
    }
}
