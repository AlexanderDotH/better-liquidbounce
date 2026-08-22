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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaceClipResearchControlTest {

    @Test
    fun `active probe remote route and unsafe context are refused before execution`() {
        var activeProbe = true
        var activeRoute = false
        var unsafe = false
        var starts = 0
        val control = MaceClipResearchGuardedControl(
            hasActiveProbe = { activeProbe },
            hasActiveRemoteKillSession = { activeRoute },
            hasUnsafeMovementContext = { unsafe },
            startExecution = {
                starts++
                MaceClipResearchProbeStartResult.STARTED
            },
            statusProvider = { MaceClipResearchStatus.Idle },
            abortExecution = { MaceClipResearchAbortResult.IDLE },
        )

        assertEquals(MaceClipResearchProbeStartResult.ACTIVE_PROBE, control.startProbe(request()))
        activeProbe = false
        activeRoute = true
        assertEquals(MaceClipResearchProbeStartResult.ACTIVE_REMOTE_KILL_SESSION, control.startProbe(request()))
        activeRoute = false
        unsafe = true
        assertEquals(MaceClipResearchProbeStartResult.UNSAFE_CONTEXT, control.startProbe(request()))
        unsafe = false
        assertEquals(MaceClipResearchProbeStartResult.STARTED, control.startProbe(request()))
        assertEquals(1, starts)
    }

    private fun request() = MaceClipResearchProbeRequest.Attack(
        primingPackets = 9,
        packetShape = MaceClipResearchPacketShape.POSITION,
        clearance = 99.0,
        phaseDelayTicks = 1,
        terminalHoldTicks = 2,
    )
}
