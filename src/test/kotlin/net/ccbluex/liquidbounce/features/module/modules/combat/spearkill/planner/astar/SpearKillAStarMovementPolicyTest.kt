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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar


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
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillAStarMovementPolicyTest {

    @Test
    fun `terminal validation accepts only the exact bounded lateral suffix`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3.ZERO,
            terminalWaypoint = Vec3(7.0, 0.0, 0.0),
        )

        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(0.0, 4.0, 0.0), Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 4.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 2.99,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.1)),
            approach = approach,
            stepLimit = 4.0,
        ))
    }

    @Test
    fun `bounded movements append fixed packets in exact outbound order`() {
        val destination = mutableListOf<Vec3>()
        val validatedEdges = mutableListOf<Pair<Vec3, Vec3>>()

        assertTrue(appendSpearKillAStarBoundedMovements(
            from = Vec3.ZERO,
            to = Vec3(7.0, 0.0, 0.0),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                validatedEdges += from to to
                true
            },
            destination = destination,
            maxVerticalStep = 3.0,
        ))

        assertEquals(listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)), destination)
        assertEquals(
            listOf(
                Vec3.ZERO to Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0) to Vec3(6.0, 0.0, 0.0),
                Vec3(6.0, 0.0, 0.0) to Vec3(7.0, 0.0, 0.0),
            ),
            validatedEdges,
        )
    }

    @Test
    fun `blocked bounded movement stops immediately and retains only validated prefix`() {
        val destination = mutableListOf<Vec3>()
        val validatedEdges = mutableListOf<Pair<Vec3, Vec3>>()

        assertFalse(appendSpearKillAStarBoundedMovements(
            from = Vec3.ZERO,
            to = Vec3(7.0, 0.0, 0.0),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                validatedEdges += from to to
                from.x < 3.0
            },
            destination = destination,
            maxVerticalStep = 3.0,
        ))

        assertEquals(listOf(Vec3(3.0, 0.0, 0.0)), destination)
        assertEquals(
            listOf(
                Vec3.ZERO to Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0) to Vec3(6.0, 0.0, 0.0),
            ),
            validatedEdges,
        )
    }
}
