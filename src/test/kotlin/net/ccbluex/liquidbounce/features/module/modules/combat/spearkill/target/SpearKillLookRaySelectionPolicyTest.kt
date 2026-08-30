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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillLookRayPriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillLookRaySelectionPolicyTest {

    @Test
    fun `directly hovered candidate wins normal routing`() {
        val offAxis = candidate("off-axis", distanceSquared = 16.0, directlyHovered = false)
        val directlyHovered = candidate("hovered", distanceSquared = 36.0, directlyHovered = true)

        assertEquals(
            directlyHovered,
            selectBestSpearKillLookRayCandidate(sequenceOf(offAxis, directlyHovered), throughTerrain = false),
        )
    }

    @Test
    fun `through terrain routing prefers farther candidate at equal angle`() {
        val nearer = candidate("near", distanceSquared = 25.0, distanceAlongRaySquared = 25.0)
        val farther = candidate("far", distanceSquared = 225.0, distanceAlongRaySquared = 225.0)

        assertEquals(
            farther,
            selectBestSpearKillLookRayCandidate(sequenceOf(nearer, farther), throughTerrain = true),
        )
    }

    @Test
    fun `exact priority tie retains first world iteration candidate`() {
        val first = candidate("first", distanceSquared = 49.0)
        val second = candidate("second", distanceSquared = 49.0)

        assertEquals(
            first,
            selectBestSpearKillLookRayCandidate(sequenceOf(first, second), throughTerrain = false),
        )
    }

    private fun candidate(
        target: String,
        distanceSquared: Double,
        directlyHovered: Boolean = false,
        distanceAlongRaySquared: Double = distanceSquared,
    ) = SpearKillLookRayCandidate(
        target = target,
        distanceSquared = distanceSquared,
        priority = SpearKillLookRayPriority(
            directlyHovered = directlyHovered,
            angularErrorSquared = 0.0,
            distanceAlongRaySquared = distanceAlongRaySquared,
        ),
    )
}
