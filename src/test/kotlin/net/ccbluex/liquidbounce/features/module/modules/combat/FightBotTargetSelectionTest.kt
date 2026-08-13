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

class FightBotTargetSelectionTest {

    private data class Candidate(
        val name: String,
        val distance: Double,
        val safety: FightBotTargetSafety = FightBotTargetSafety(),
    )

    @Test
    fun `Nearest selects the strictly closest eligible entity regardless of entity type`() {
        val farPlayer = Candidate("FarPlayer", 18.0)
        val closeMob = Candidate("CloseMob", 3.0)

        val selected = selectFightBotTarget(
            mode = FightBotTargetMode.Nearest,
            configuredName = "",
            candidates = listOf(farPlayer, closeMob),
            nameOf = Candidate::name,
            distanceOf = Candidate::distance,
            isEligible = { it.safety.isEligible },
        )

        assertEquals(closeMob, selected)
    }

    @Test
    fun `Named selects only the configured eligible entity and never falls back`() {
        val configured = Candidate("MainClient", 12.0)
        val bystander = Candidate("Bystander", 2.0)

        assertEquals(
            configured,
            selectFightBotTarget(
                mode = FightBotTargetMode.Named,
                configuredName = "  mainclient ",
                candidates = listOf(bystander, configured),
                nameOf = Candidate::name,
                distanceOf = Candidate::distance,
                isEligible = { true },
            ),
        )
        assertNull(
            selectFightBotTarget(
                mode = FightBotTargetMode.Named,
                configuredName = "MissingClient",
                candidates = listOf(bystander, configured),
                nameOf = Candidate::name,
                distanceOf = Candidate::distance,
                isEligible = { true },
            ),
        )
    }

    @Test
    fun `every shared safety rejection excludes a candidate`() {
        val rejected = listOf(
            FightBotTargetSafety(self = true),
            FightBotTargetSafety(alive = false),
            FightBotTargetSafety(removed = true),
            FightBotTargetSafety(withinRange = false),
            FightBotTargetSafety(withinFov = false),
            FightBotTargetSafety(hurtTimeAccepted = false),
            FightBotTargetSafety(visible = false),
            FightBotTargetSafety(supported = false),
        )

        assertTrue(rejected.none(FightBotTargetSafety::isEligible))
    }

    @Test
    fun `friends teammates and AntiBot rejects fail the shared combat filter`() {
        listOf("friend", "teammate", "AntiBot").forEach { rejectedKind ->
            assertFalse(FightBotTargetSafety(combatSafe = false).isEligible, rejectedKind)
        }
    }
}
