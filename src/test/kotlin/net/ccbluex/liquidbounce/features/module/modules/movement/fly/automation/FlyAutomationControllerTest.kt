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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FlyAutomationControllerTest {

    @Test
    fun `disabled Fly is enabled and released only by its owning lease`() {
        val runtime = FakeFlyRuntime(enabled = false)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        assertEquals(FlyAutomationOwnership.BARITONE, lease.ownership)
        assertEquals(1, runtime.moduleEnableCount)
        assertIs<FlyAutomationLeaseValidation.Valid>(controller.validate(lease))

        controller.release(lease)
        controller.release(lease)

        assertEquals(1, runtime.moduleDisableCount)
        assertFalse(runtime.enabled)
    }

    @Test
    fun `user-owned Fly is runtime suspended without toggling its setting`() {
        val runtime = FakeFlyRuntime(enabled = true)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        assertEquals(FlyAutomationOwnership.USER, lease.ownership)
        assertTrue(controller.temporarilySuspend(lease))
        assertTrue(controller.temporarilySuspend(lease))
        assertTrue(runtime.enabled)
        assertTrue(controller.runtimeSuspended)
        assertEquals(1, runtime.modeDisableCount)

        assertTrue(controller.resume(lease))
        assertTrue(controller.resume(lease))
        assertEquals(1, runtime.modeEnableCount)
        assertFalse(controller.runtimeSuspended)

        controller.release(lease)
        assertTrue(runtime.enabled)
        assertEquals(0, runtime.moduleDisableCount)
    }

    @Test
    fun `Baritone-owned Fly is disabled during fallback and re-enabled once`() {
        val runtime = FakeFlyRuntime(enabled = false)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        assertTrue(controller.temporarilySuspend(lease))
        assertFalse(runtime.enabled)
        assertEquals(1, runtime.moduleDisableCount)
        assertTrue(controller.resume(lease))
        assertTrue(runtime.enabled)
        assertEquals(2, runtime.moduleEnableCount)

        controller.release(lease)
        assertEquals(2, runtime.moduleDisableCount)
    }

    @Test
    fun `manual mode change invalidates lease and suppresses stale steering`() {
        val runtime = FakeFlyRuntime(enabled = true)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease
        assertTrue(controller.applySteering(lease, FlySteeringIntent(Vec3(0.0, 0.0, 1.0))))

        runtime.selectedModeName = "Packet"
        controller.onSelectedModeChanged("Packet")
        runtime.selectedModeName = "Vanilla"

        assertIs<FlyAutomationLeaseValidation.Invalid>(controller.validate(lease))
        assertEquals(null, controller.activeIntent())
    }

    @Test
    fun `leases are generation tagged and stale cleanup is idempotent`() {
        val runtime = FakeFlyRuntime(enabled = true)
        val controller = FlyAutomationController(runtime)
        val first = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease
        controller.release(first)
        val second = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        assertTrue(second.generation > first.generation)
        controller.clearSteering(first)
        controller.release(first)

        assertIs<FlyAutomationLeaseValidation.Valid>(controller.validate(second))
    }

    @Test
    fun `manual Fly toggle transfers ownership and is not undone by release`() {
        val runtime = FakeFlyRuntime(enabled = false)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        runtime.enabled = false
        controller.onModuleStateChanged(enabled = false)
        runtime.enabled = true
        controller.onModuleStateChanged(enabled = true)
        controller.release(lease)

        assertTrue(runtime.enabled)
        assertEquals(0, runtime.moduleDisableCount)
    }

    @Test
    fun `releasing suspended user Fly starts the newly selected mode without restoring the old choice`() {
        val runtime = FakeFlyRuntime(enabled = true)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease
        controller.temporarilySuspend(lease)

        runtime.selectedModeName = "Packet"
        controller.onSelectedModeChanged("Packet")
        controller.release(lease)

        assertEquals("Packet", runtime.selectedModeName)
        assertEquals(1, runtime.modeDisableCount)
        assertEquals(1, runtime.modeEnableCount)
        assertTrue(runtime.enabled)
    }

    @Test
    fun `Baritone-owned automatic self-disable remains consumable and restartable`() {
        val profile = EndProfile()
        val runtime = FakeFlyRuntime(enabled = false, selectedProfile = profile)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        profile.mark("burst ended")
        runtime.enabled = false
        controller.onModuleStateChanged(enabled = false)

        assertIs<FlyAutomationLeaseValidation.Valid>(controller.validate(lease))
        assertEquals("burst ended", controller.consumeAutomaticEnd(lease)?.reason)
        assertTrue(controller.resume(lease))
        assertTrue(runtime.enabled)
        assertEquals(2, runtime.moduleEnableCount)
    }

    @Test
    fun `user-owned automatic self-disable is reported without restoring Fly`() {
        val profile = EndProfile()
        val runtime = FakeFlyRuntime(enabled = true, selectedProfile = profile)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        profile.mark("one shot completed")
        runtime.enabled = false
        controller.onModuleStateChanged(enabled = false)

        assertEquals("one shot completed", controller.consumeAutomaticEnd(lease)?.reason)
        controller.release(lease)
        assertFalse(runtime.enabled)
        assertEquals(0, runtime.moduleEnableCount)
    }

    @Test
    fun `module-level automatic setback end survives the disable callback`() {
        val runtime = FakeFlyRuntime(enabled = false)
        val controller = FlyAutomationController(runtime)
        val lease = assertIs<FlyAutomationAcquireResult.Acquired>(controller.acquire()).lease

        controller.markAutomaticEnd("server setback")
        runtime.enabled = false
        controller.onModuleStateChanged(enabled = false)

        assertIs<FlyAutomationLeaseValidation.Valid>(controller.validate(lease))
        assertEquals("server setback", controller.consumeAutomaticEnd(lease)?.reason)
    }

    private class FakeFlyRuntime(
        override var enabled: Boolean,
        override var selectedModeName: String = "Vanilla",
        override var selectedProfile: FlyAutomationProfile? = TestProfile,
    ) : FlyAutomationRuntime {

        var moduleEnableCount = 0
        var moduleDisableCount = 0
        var modeEnableCount = 0
        var modeDisableCount = 0

        override fun setModuleEnabled(enabled: Boolean) {
            if (this.enabled == enabled) return
            this.enabled = enabled
            if (enabled) moduleEnableCount++ else moduleDisableCount++
        }

        override fun enableSelectedMode() {
            modeEnableCount++
        }

        override fun disableSelectedMode() {
            modeDisableCount++
        }
    }

    private object TestProfile : FlyAutomationProfile {
        override val automationCapabilities = FlyAutomationCapabilities(
            horizontal = true,
            ascend = true,
            descend = true,
            landing = true,
            kind = FlyAutomationKind.CONTINUOUS,
        )

        override fun automationReadiness() = FlyAutomationReadiness.Ready
    }

    private class EndProfile : FlyAutomationProfile {
        override val automationCapabilities = TestProfile.automationCapabilities
        private var pending: FlyAutomationEnd? = null

        override fun automationReadiness() = FlyAutomationReadiness.Ready

        override fun consumeAutomaticEnd(): FlyAutomationEnd? = pending.also { pending = null }

        fun mark(reason: String) {
            pending = FlyAutomationEnd(reason)
        }
    }
}
