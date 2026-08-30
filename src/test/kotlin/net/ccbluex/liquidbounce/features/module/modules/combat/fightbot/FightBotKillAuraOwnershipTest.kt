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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

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
