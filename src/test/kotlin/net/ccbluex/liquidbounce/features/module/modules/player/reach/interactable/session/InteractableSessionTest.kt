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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableSessionTest {

    @Test
    fun `planning locks one target and captures its immutable session settings`() {
        val session = InteractableSession<String, TestPayload>()
        val settings = InteractableSessionSettings(
            openRetries = 2,
            openTimeoutTicks = 20,
            routeTimeoutTicks = 400,
            holdTimeoutTicks = 0,
        )

        assertTrue(session.beginPlanning("chest:10,64,10", Vec3(10.25, 64.0, -3.75), settings, tick = 12))
        assertFalse(session.beginPlanning("barrel:20,64,20", Vec3.ZERO, settings, tick = 13))

        assertEquals(InteractableSessionState.Planning(startedTick = 12), session.state)
        assertEquals("chest:10,64,10", session.target)
        assertEquals(Vec3(10.25, 64.0, -3.75), session.origin)
        assertEquals(settings, session.settings)
        assertTrue(session.movementLeaseRequired)
    }

    @Test
    fun `route rejects a return that does not end at the preceding checkpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            InteractableSessionRoute(
                origin = Vec3.ZERO,
                steps = listOf(
                    InteractableRouteStep(
                        movement("out", 4.0),
                        inverse = listOf(movement("bad-return", 1.0)),
                    ),
                ),
            )
        }
    }

    @Test
    fun `cancelled queued and dropped packets retry the same step without advancing`() {
        InteractablePacketDisposition.entries.filterNot { it == InteractablePacketDisposition.DELIVERED }.forEach {
            disposition ->
            val session = startedSession()
            val packet = Any()

            val prepared = session.prepareMovement(packet)
            val confirmation = session.confirmMovement(packet, disposition, tick = 12)

            assertEquals(TestPayload("out-1"), prepared?.payload)
            assertTrue(confirmation.matchedPacket)
            assertFalse(confirmation.committed)
            assertEquals(Vec3.ZERO, session.confirmedPosition)
            assertEquals(0, (session.state as InteractableSessionState.Outbound).confirmedSteps)
            assertEquals(TestPayload("out-1"), session.prepareMovement(Any())?.payload)
        }
    }

    @Test
    fun `only the exact pending packet identity can confirm one outbound step`() {
        val session = startedSession()
        val ownedPacket = Any()
        val unrelatedPacket = Any()

        session.prepareMovement(ownedPacket)

        val ignored = session.confirmMovement(
            unrelatedPacket,
            InteractablePacketDisposition.DELIVERED,
            tick = 12,
        )
        val committed = session.confirmMovement(
            ownedPacket,
            InteractablePacketDisposition.DELIVERED,
            tick = 12,
        )

        assertFalse(ignored.matchedPacket)
        assertFalse(ignored.committed)
        assertTrue(committed.matchedPacket)
        assertTrue(committed.committed)
        assertEquals(Vec3(2.0, 0.0, 0.0), session.confirmedPosition)
        assertEquals(1, (session.state as InteractableSessionState.Outbound).confirmedSteps)
        assertFalse(
            session.confirmMovement(ownedPacket, InteractablePacketDisposition.DELIVERED, tick = 12).matchedPacket,
        )
    }

    @Test
    fun `final outbound confirmation enters Opening and requests the first interaction`() {
        val session = startedSession(singleStepRoute())
        val packet = Any()

        session.prepareMovement(packet)
        val result = session.confirmMovement(packet, InteractablePacketDisposition.DELIVERED, tick = 15)

        assertEquals(InteractableSessionState.Opening(attemptsSent = 1, attemptStartedTick = 15), session.state)
        assertEquals(listOf(InteractableSessionEffect.OpenAttempt(1)), result.effects)
        assertEquals(Vec3(2.0, 0.0, 0.0), session.serverAnchorPosition)
        assertNull(session.prepareMovement(Any()))
    }

    @Test
    fun `endpoint verification waits for correction-free ticks before starting the open timer`() {
        val session = startedSession(
            singleStepRoute(),
            settings(endpointVerifyTicks = 2),
        )
        val packet = Any()

        session.prepareMovement(packet)
        val confirmation = session.confirmMovement(packet, InteractablePacketDisposition.DELIVERED, tick = 15)

        assertTrue(confirmation.effects.isEmpty())
        assertEquals(InteractableSessionState.Opening(attemptsSent = 0, attemptStartedTick = 15), session.state)
        assertTrue(session.tick(tick = 16).isEmpty())
        assertEquals(listOf(InteractableSessionEffect.OpenAttempt(1)), session.tick(tick = 17))
        assertEquals(InteractableSessionState.Opening(attemptsSent = 1, attemptStartedTick = 17), session.state)
    }

    @Test
    fun `opening retries twice at the configured timeout then recovers exactly`() {
        val session = openedSession()

        assertEquals(emptyList<InteractableSessionEffect>(), session.tick(tick = 34))
        assertEquals(listOf(InteractableSessionEffect.OpenAttempt(2)), session.tick(tick = 35))
        assertEquals(listOf(InteractableSessionEffect.OpenAttempt(3)), session.tick(tick = 55))

        val effects = session.tick(tick = 75)

        assertTrue(session.state is InteractableSessionState.Recovering)
        assertEquals(
            listOf(
                InteractableSessionEffect.RecoveryStarted(
                    InteractableSessionCause.OPEN_TIMEOUT,
                    Vec3(2.0, 0.0, 0.0),
                ),
            ),
            effects,
        )
        assertEquals(TestPayload("back-1"), session.prepareMovement(Any())?.payload)
    }

    @Test
    fun `first matching opened container is owned and an indefinite hold never times out`() {
        val session = openedSession()

        assertTrue(session.claimOpenedContainer(containerId = 7, tick = 16))
        assertFalse(session.claimOpenedContainer(containerId = 8, tick = 17))

        assertEquals(InteractableSessionState.Holding(containerId = 7, startedTick = 16), session.state)
        assertEquals(7, session.ownedContainerId)
        assertTrue(session.isOwnedContainer(7))
        assertFalse(session.isOwnedContainer(8))
        assertTrue(session.suppressMovementInput)
        assertEquals(emptyList<InteractableSessionEffect>(), session.tick(tick = Int.MAX_VALUE))
    }

    @Test
    fun `user close returns by the exact composite inverse and releases at the origin`() {
        val session = holdingSession(twoStepRoute())

        val returnEffects = session.containerClosed(7, InteractableContainerCloseCause.USER, tick = 20)
        val firstPacket = Any()
        val firstReturn = session.prepareMovement(firstPacket)
        val firstConfirmation = session.confirmMovement(
            firstPacket,
            InteractablePacketDisposition.DELIVERED,
            tick = 21,
        )
        val secondPacket = Any()
        val secondReturn = session.prepareMovement(secondPacket)
        val completion = session.confirmMovement(
            secondPacket,
            InteractablePacketDisposition.DELIVERED,
            tick = 22,
        )

        assertEquals(
            listOf(InteractableSessionEffect.ReturnStarted(InteractableSessionCause.USER_CLOSE, Vec3(5.0, 0.0, 0.0))),
            returnEffects,
        )
        assertEquals(TestPayload("back-2"), firstReturn?.payload)
        assertTrue(firstConfirmation.committed)
        assertEquals(TestPayload("back-1"), secondReturn?.payload)
        assertEquals(
            listOf(InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.COMPLETED)),
            completion.effects,
        )
        assertEquals(InteractableSessionState.Idle, session.state)
        assertEquals(Vec3.ZERO, session.confirmedPosition)
        assertFalse(session.movementLeaseRequired)
    }

    @Test
    fun `server close for another container cannot release the owned session`() {
        val session = holdingSession()

        assertEquals(emptyList<InteractableSessionEffect>(), session.containerClosed(
            8,
            InteractableContainerCloseCause.SERVER,
            tick = 20,
        ))
        assertTrue(session.state is InteractableSessionState.Holding)
    }

    @Test
    fun `finite hold timeout closes the owned menu and starts normal return`() {
        val session = openedSession(settings(holdTimeoutTicks = 40))
        session.claimOpenedContainer(containerId = 7, tick = 16)

        assertEquals(emptyList<InteractableSessionEffect>(), session.tick(tick = 55))
        assertEquals(
            listOf(
                InteractableSessionEffect.CloseOwnedContainer(7),
                InteractableSessionEffect.ReturnStarted(InteractableSessionCause.HOLD_TIMEOUT, Vec3(2.0, 0.0, 0.0)),
            ),
            session.tick(tick = 56),
        )
        assertTrue(session.state is InteractableSessionState.Returning)
    }

    @Test
    fun `disable while holding closes the menu and recovers without abandoning the endpoint`() {
        val session = holdingSession()

        val effects = session.abort(InteractableSessionCause.DISABLE, tick = 30)

        assertEquals(
            listOf(
                InteractableSessionEffect.CloseOwnedContainer(7),
                InteractableSessionEffect.RecoveryStarted(InteractableSessionCause.DISABLE, Vec3(2.0, 0.0, 0.0)),
            ),
            effects,
        )
        assertTrue(session.state is InteractableSessionState.Recovering)
        assertTrue(session.movementLeaseRequired)
    }

    @Test
    fun `target loss midway returns only the confirmed outbound prefix`() {
        val session = startedSession(twoStepRoute())
        deliverNext(session, tick = 12)

        val effects = session.abort(InteractableSessionCause.TARGET_LOST, tick = 13)

        assertEquals(
            listOf(InteractableSessionEffect.RecoveryStarted(
                InteractableSessionCause.TARGET_LOST,
                Vec3(2.0, 0.0, 0.0),
            )),
            effects,
        )
        assertEquals(TestPayload("back-1"), session.prepareMovement(Any())?.payload)
    }

    @Test
    fun `planning timeout releases immediately because no movement was confirmed`() {
        val session = InteractableSession<String, TestPayload>()
        session.beginPlanning("chest", Vec3.ZERO, settings(routeTimeoutTicks = 40), tick = 10)

        assertEquals(emptyList<InteractableSessionEffect>(), session.tick(tick = 49))
        assertEquals(
            listOf(InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.PLANNING_TIMEOUT)),
            session.tick(tick = 50),
        )
        assertEquals(InteractableSessionState.Idle, session.state)
    }

    @Test
    fun `outbound route timeout changes to confirmed-prefix recovery`() {
        val session = startedSession(twoStepRoute(), settings(routeTimeoutTicks = 40), startTick = 10)
        deliverNext(session, tick = 12)

        val effects = session.tick(tick = 50)

        assertEquals(
            listOf(InteractableSessionEffect.RecoveryStarted(
                InteractableSessionCause.ROUTE_TIMEOUT,
                Vec3(2.0, 0.0, 0.0),
            )),
            effects,
        )
        assertTrue(session.state is InteractableSessionState.Recovering)
        assertEquals(TestPayload("back-1"), session.prepareMovement(Any())?.payload)
    }

    @Test
    fun `correction at a confirmed checkpoint installs its known exact recovery`() {
        val session = startedSession(twoStepRoute())
        deliverNext(session, tick = 12)

        val decision = session.corrected(Vec3(2.0, 0.0, 0.0), validatedRecovery = null, tick = 13)

        assertTrue(decision is InteractableCorrectionDecision.Recovering)
        assertTrue(session.state is InteractableSessionState.Recovering)
        assertEquals(TestPayload("back-1"), session.prepareMovement(Any())?.payload)
    }

    @Test
    fun `bounded validated correction recovery can be installed from an unknown checkpoint`() {
        val session = openedSession()
        val recovery = listOf(
            InteractableMovement(TestPayload("validated-a"), Vec3(1.0, 0.0, 0.0)),
            InteractableMovement(TestPayload("validated-b"), Vec3.ZERO),
        )

        val decision = session.corrected(Vec3(1.5, 0.0, 0.0), recovery, tick = 20)

        assertTrue(decision is InteractableCorrectionDecision.Recovering)
        assertEquals(TestPayload("validated-a"), session.prepareMovement(Any())?.payload)
    }

    @Test
    fun `unknown unsafe correction accepts the server position and releases the lease`() {
        val session = holdingSession()
        val correctedPosition = Vec3(100.0, 20.0, -30.0)

        val decision = session.corrected(correctedPosition, validatedRecovery = null, tick = 20)

        assertTrue(decision is InteractableCorrectionDecision.AcceptLocally)
        assertEquals(
            listOf(
                InteractableSessionEffect.CloseOwnedContainer(7),
                InteractableSessionEffect.AcceptCorrectionLocally(correctedPosition),
                InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
            ),
            (decision as InteractableCorrectionDecision.AcceptLocally).effects,
        )
        assertEquals(InteractableSessionState.Idle, session.state)
        assertEquals(correctedPosition, session.confirmedPosition)
    }

    @Test
    fun `world change disconnect and death hard reset without attempting hidden movement`() {
        listOf(
            InteractableSessionCause.WORLD_CHANGE,
            InteractableSessionCause.DISCONNECT,
            InteractableSessionCause.DEATH,
        ).forEach { cause ->
            val session = holdingSession()

            val effects = session.hardReset(cause)

            assertEquals(listOf(InteractableSessionEffect.ReleaseMovementLease(cause)), effects)
            assertEquals(InteractableSessionState.Idle, session.state)
            assertFalse(session.movementLeaseRequired)
        }
    }

    @Test
    fun `cancelled recovery packet commits no prefix and retries with a new identity`() {
        val session = holdingSession()
        session.abort(InteractableSessionCause.DISABLE, tick = 20)
        val firstPacket = Any()
        val firstMovement = session.prepareMovement(firstPacket)

        val cancelled = session.confirmMovement(
            firstPacket,
            InteractablePacketDisposition.QUEUED,
            tick = 21,
        )
        val retriedMovement = session.prepareMovement(Any())

        assertFalse(cancelled.committed)
        assertSame(firstMovement, retriedMovement)
        assertEquals(Vec3(2.0, 0.0, 0.0), session.confirmedPosition)
    }

    @Test
    fun `partial VClip burst commits only its delivered status-only prefix`() {
        val route = InteractableSessionRoute(
            origin = Vec3.ZERO,
            steps = listOf(
                InteractableRouteStep(
                    InteractableMovement(TestPayload("down-primer"), Vec3.ZERO),
                    inverse = emptyList(),
                ),
                InteractableRouteStep(
                    InteractableMovement(TestPayload("down-position"), Vec3(0.0, -20.0, 0.0)),
                    inverse = listOf(
                        InteractableMovement(TestPayload("up-primer"), Vec3(0.0, -20.0, 0.0)),
                        InteractableMovement(TestPayload("up-position"), Vec3.ZERO),
                    ),
                ),
            ),
        )
        val session = startedSession(route)
        deliverNext(session, tick = 12)
        val rejectedPacket = Any()
        session.prepareMovement(rejectedPacket)

        val effects = session.rejectMovement(
            rejectedPacket,
            InteractablePacketDisposition.QUEUED,
            InteractableSessionCause.ROUTE_BLOCKED,
            tick = 12,
        )

        assertEquals(Vec3.ZERO, session.confirmedPosition)
        assertEquals(
            listOf(InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.ROUTE_BLOCKED)),
            effects,
        )
        assertEquals(InteractableSessionState.Idle, session.state)
    }

    @Test
    fun `stalled recovery resynchronizes locally and releases its movement lease`() {
        val session = holdingSession()
        session.abort(InteractableSessionCause.DISABLE, tick = 20)

        val effects = session.tick(tick = 420)

        assertEquals(
            listOf(
                InteractableSessionEffect.AcceptCorrectionLocally(Vec3(2.0, 0.0, 0.0)),
                InteractableSessionEffect.ReleaseMovementLease(InteractableSessionCause.RESYNC_REQUIRED),
            ),
            effects,
        )
        assertEquals(InteractableSessionState.Idle, session.state)
    }

    @Test
    fun `delivered VClip position recovers through its explicit multi-packet inverse`() {
        val route = InteractableSessionRoute(
            origin = Vec3.ZERO,
            steps = listOf(
                InteractableRouteStep(
                    InteractableMovement(TestPayload("down"), Vec3(0.0, -20.0, 0.0)),
                    inverse = listOf(
                        InteractableMovement(TestPayload("up-primer"), Vec3(0.0, -20.0, 0.0)),
                        InteractableMovement(TestPayload("up-position"), Vec3.ZERO),
                    ),
                ),
            ),
        )
        val session = openedSessionFor(route)

        session.abort(InteractableSessionCause.DISABLE, tick = 20)

        assertEquals(TestPayload("up-primer"), session.prepareMovement(Any())?.payload)
        val primerPacket = requireNotNull(session.pendingPacketIdentity)
        session.confirmMovement(primerPacket, InteractablePacketDisposition.DELIVERED, tick = 21)
        assertEquals(Vec3(0.0, -20.0, 0.0), session.confirmedPosition)
        assertEquals(TestPayload("up-position"), session.prepareMovement(Any())?.payload)
    }

    private fun startedSession(
        route: InteractableSessionRoute<TestPayload> = twoStepRoute(),
        settings: InteractableSessionSettings = settings(),
        startTick: Int = 10,
    ) = InteractableSession<String, TestPayload>().also { session ->
        session.beginPlanning("chest", route.origin, settings, startTick)
        assertTrue(session.acceptRoute(route, tick = startTick + 1))
    }

    private fun openedSession(settings: InteractableSessionSettings = settings()) =
        startedSession(singleStepRoute(), settings).also { session ->
            deliverNext(session, tick = 15)
        }

    private fun openedSessionFor(route: InteractableSessionRoute<TestPayload>) =
        startedSession(route).also { session ->
            repeat(route.steps.size) { deliverNext(session, tick = 15) }
        }

    private fun holdingSession(route: InteractableSessionRoute<TestPayload> = singleStepRoute()) =
        startedSession(route).also { session ->
            repeat(route.steps.size) { deliverNext(session, tick = 15) }
            assertTrue(session.claimOpenedContainer(containerId = 7, tick = 16))
        }

    private fun deliverNext(session: InteractableSession<String, TestPayload>, tick: Int) {
        val packet = Any()
        session.prepareMovement(packet)
        session.confirmMovement(packet, InteractablePacketDisposition.DELIVERED, tick)
    }

    private fun singleStepRoute() = InteractableSessionRoute(
        origin = Vec3.ZERO,
        steps = listOf(
            InteractableRouteStep(
                movement("out-1", 2.0),
                inverse = listOf(movement("back-1", 0.0)),
            ),
        ),
    )

    private fun twoStepRoute() = InteractableSessionRoute(
        origin = Vec3.ZERO,
        steps = listOf(
            InteractableRouteStep(
                movement("out-1", 2.0),
                inverse = listOf(movement("back-1", 0.0)),
            ),
            InteractableRouteStep(
                movement("out-2", 5.0),
                inverse = listOf(movement("back-2", 2.0)),
            ),
        ),
    )

    private fun movement(name: String, x: Double) =
        InteractableMovement(TestPayload(name), Vec3(x, 0.0, 0.0))

    private fun settings(
        openRetries: Int = 2,
        openTimeoutTicks: Int = 20,
        routeTimeoutTicks: Int = 400,
        holdTimeoutTicks: Int = 0,
        endpointVerifyTicks: Int = 0,
    ) = InteractableSessionSettings(
        openRetries,
        openTimeoutTicks,
        routeTimeoutTicks,
        holdTimeoutTicks,
        endpointVerifyTicks,
    )

    private data class TestPayload(val name: String)
}
