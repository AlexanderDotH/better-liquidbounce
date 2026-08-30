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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionSettings
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachInteractableControllerTest {

    @Test
    fun `normal interaction wins before ownership or extended targeting`() {
        val fixture = Fixture()

        val claimed = fixture.controller.claim(
            normalInteractionAvailable = true,
            origin = Vec3.ZERO,
            settings = settings(),
            tick = 10,
        )

        assertFalse(claimed)
        assertEquals(0, fixture.ownership.acquireCalls)
        assertEquals(0, fixture.targets.acquireCalls)
    }

    @Test
    fun `movement conflict rejects before performing the long raycast`() {
        val fixture = Fixture().apply { ownership.available = false }

        assertFalse(fixture.claim())

        assertEquals(1, fixture.ownership.acquireCalls)
        assertEquals(0, fixture.targets.acquireCalls)
        assertFalse(fixture.session.active)
        assertEquals(InteractableControllerMessage.MovementBusy, fixture.controller.lastMessage)
    }

    @Test
    fun `target rejection remains available for user-facing status`() {
        val fixture = Fixture().apply {
            targets.result = ControllerTargetResult.Rejected("filtered")
        }

        assertFalse(fixture.claim())

        assertEquals(
            InteractableControllerMessage.TargetRejected("filtered"),
            fixture.controller.lastMessage,
        )
    }

    @Test
    fun `fresh far target locks one lease and starts incremental planning`() {
        val fixture = Fixture()

        assertTrue(fixture.claim())

        assertEquals("chest", fixture.session.target)
        assertEquals(Vec3(0.25, 64.0, -0.75), fixture.session.origin)
        assertEquals(1, fixture.planner.beginCalls)
        assertTrue(fixture.ownership.lease?.active == true)
    }

    @Test
    fun `planning advances once per tick with the captured node budget`() {
        val fixture = Fixture().apply {
            planner.task.progress += ControllerRouteProgress.Running("searching")
            planner.task.progress += ControllerRouteProgress.Ready("direct-route")
        }
        assertTrue(fixture.claim(nodesPerTick = 750))

        fixture.controller.tick(21)
        fixture.controller.tick(22)

        assertEquals(listOf(750, 750), fixture.planner.task.budgets)
        assertEquals("direct-route", fixture.session.route)
        assertEquals("searching", fixture.controller.renderSnapshot)
    }

    @Test
    fun `planning failure aborts the session and releases ownership`() {
        val fixture = Fixture().apply {
            planner.task.progress += ControllerRouteProgress.Failed("no-surface")
        }
        assertTrue(fixture.claim())

        fixture.controller.tick(21)

        assertEquals(InteractableSessionCause.PLANNING_FAILED, fixture.session.abortCause)
        assertFalse(fixture.ownership.lease!!.active)
        assertNull(fixture.controller.renderSnapshot)
        assertEquals(
            InteractableControllerMessage.RouteFailed("no-surface"),
            fixture.controller.lastMessage,
        )
    }

    @Test
    fun `disable retains ownership until exact return completes`() {
        val fixture = Fixture()
        assertTrue(fixture.claim())
        fixture.session.keepActiveOnAbort = true

        fixture.controller.abort(InteractableSessionCause.DISABLE, tick = 25)

        assertTrue(fixture.ownership.lease!!.active)
        assertTrue(fixture.planner.task.cancelled)

        fixture.session.active = false
        fixture.controller.tick(26)
        assertFalse(fixture.ownership.lease!!.active)
    }

    @Test
    fun `world reset releases ownership immediately and clears the planner`() {
        val fixture = Fixture()
        assertTrue(fixture.claim())

        fixture.controller.hardReset(InteractableSessionCause.WORLD_CHANGE)

        assertTrue(fixture.planner.task.cancelled)
        assertTrue(fixture.session.hardReset)
        assertFalse(fixture.ownership.lease!!.active)
    }

    private class Fixture {
        val ownership = FakeOwnership()
        val targets = FakeTargets()
        val planner = FakePlanner()
        val session = FakeSession()
        val controller = ReachInteractableController(ownership, targets, planner, session)

        fun claim(nodesPerTick: Int = 750) = controller.claim(
            normalInteractionAvailable = false,
            origin = Vec3(0.25, 64.0, -0.75),
            settings = settings(nodesPerTick),
            tick = 20,
        )
    }

    private class FakeOwnership : ControllerMovementOwnership {
        var available = true
        var acquireCalls = 0
        var lease: FakeLease? = null

        override fun tryAcquire(owner: String): ControllerMovementLease? {
            acquireCalls++
            return if (available) FakeLease().also { lease = it } else null
        }
    }

    private class FakeLease : ControllerMovementLease {
        override var active = true
            private set

        override fun close() {
            active = false
        }
    }

    private class FakeTargets : ControllerTargetPort<String> {
        var acquireCalls = 0
        var result: ControllerTargetResult<String> = ControllerTargetResult.Acquired("chest")

        override fun acquire(settings: InteractableSettingsSnapshot): ControllerTargetResult<String> {
            acquireCalls++
            return result
        }

        override fun validate(target: String): Boolean = target == "chest"
    }

    private class FakePlanner : ControllerRoutePort<String, String, String> {
        var beginCalls = 0
        val task = FakeRouteTask()

        override fun begin(
            target: String,
            origin: Vec3,
            settings: InteractableSettingsSnapshot,
        ): ControllerRouteTask<String, String> {
            beginCalls++
            return task
        }
    }

    private class FakeRouteTask : ControllerRouteTask<String, String> {
        val progress = ArrayDeque<ControllerRouteProgress<String, String>>()
        val budgets = mutableListOf<Int>()
        var cancelled = false

        override fun advance(nodes: Int): ControllerRouteProgress<String, String> {
            budgets += nodes
            return progress.removeFirstOrNull() ?: ControllerRouteProgress.Running("searching")
        }

        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeSession : ControllerSessionPort<String, String> {
        override var active = false
        var keepActiveOnAbort = false
        var target: String? = null
        var origin: Vec3? = null
        var route: String? = null
        var abortCause: InteractableSessionCause? = null
        var hardReset = false

        override fun beginPlanning(
            target: String,
            origin: Vec3,
            settings: InteractableSessionSettings,
            tick: Int,
        ): Boolean {
            if (active) return false
            active = true
            this.target = target
            this.origin = origin
            return true
        }

        override fun acceptRoute(route: String, tick: Int) {
            this.route = route
        }

        override fun tick(tick: Int) = Unit

        override fun abort(cause: InteractableSessionCause, tick: Int) {
            abortCause = cause
            active = keepActiveOnAbort
        }

        override fun hardReset(cause: InteractableSessionCause) {
            active = false
            hardReset = true
        }
    }
}

private fun settings(nodesPerTick: Int = 750) = InteractableSettingsSnapshot(
    maxRange = 256.0,
    interactionRange = 4.5,
    filter = InteractableBlockFilter(net.ccbluex.liquidbounce.utils.collection.Filter.BLACKLIST, emptySet()),
    containerVehicles = true,
    routing = InteractableRoutingSettings(
        maxCost = 4096,
        diagonal = true,
        lineOfSightShortcuts = true,
        stepDistance = 9.5,
        stepDelayTicks = 0,
        nodesPerTick = nodesPerTick,
        renderPath = true,
    ),
    surfaceFallback = InteractableSurfaceFallbackSettings(
        enabled = true,
        maxRise = 128,
        horizontalSearch = 48,
        maxClipDistance = 30,
        doNotClipAroundBedrock = true,
        transport = InteractableVClipSettings.Vanilla(false, false),
    ),
    openRetries = 2,
    openTimeoutTicks = 20,
    routeTimeoutTicks = 400,
    holdTimeoutTicks = 0,
)
