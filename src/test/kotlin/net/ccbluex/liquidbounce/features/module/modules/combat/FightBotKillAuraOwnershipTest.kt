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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightBotKillAuraOwnershipTest {

    @Test
    fun `locked handoff excludes ordinary scanning and crosshair retargeting`() {
        assertEquals(
            "FightBotTarget",
            selectKillAuraTargetForFightBot(
                handoff = FightBotHandoffState.Locked,
                lockedTarget = "FightBotTarget",
                trackedTarget = "OldAuraTarget",
                crosshairTarget = "Bystander",
            ),
        )
        assertNull(
            selectKillAuraTargetForFightBot(
                handoff = FightBotHandoffState.Idle,
                lockedTarget = null,
                trackedTarget = "OldAuraTarget",
                crosshairTarget = "Bystander",
            ),
        )
    }

    @Test
    fun `inactive handoff preserves ordinary KillAura crosshair behavior`() {
        assertEquals(
            "CrosshairTarget",
            selectKillAuraTargetForFightBot(
                handoff = FightBotHandoffState.Inactive,
                lockedTarget = null,
                trackedTarget = "TrackedTarget",
                crosshairTarget = "CrosshairTarget",
            ),
        )
    }

    @Test
    fun `FightBot leases only a KillAura it had to enable`() {
        val initiallyOff = FightBotKillAuraLease.start(autoEnable = true, killAuraEnabled = false)
        val initiallyOn = FightBotKillAuraLease.start(autoEnable = true, killAuraEnabled = true)

        assertTrue(initiallyOff.enableKillAura)
        assertTrue(initiallyOff.ownsKillAura)
        assertTrue(initiallyOff.shouldDisableKillAuraOnRelease)

        assertFalse(initiallyOn.enableKillAura)
        assertFalse(initiallyOn.ownsKillAura)
        assertFalse(initiallyOn.shouldDisableKillAuraOnRelease)
    }

    @Test
    fun `manual KillAura disable halts the lease without requesting another enable`() {
        val halted = FightBotKillAuraLease.start(autoEnable = true, killAuraEnabled = false)
            .onKillAuraDisabled()

        assertTrue(halted.halted)
        assertFalse(halted.enableKillAura)
        assertFalse(halted.isOperational(killAuraEnabled = true))
    }
}
