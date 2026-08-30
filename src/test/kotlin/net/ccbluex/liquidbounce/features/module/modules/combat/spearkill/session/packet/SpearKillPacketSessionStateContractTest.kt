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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
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
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillPacketSessionStateContractTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `cancelled delivery retries the same step without committing or waiting`() {
        val movement = Vec3(3.0, 0.0, 0.0)
        val session = SpearKillPacketSessionState().apply {
            start(
                path = listOf(movement, movement.scale(-1.0), Vec3.ZERO),
                outboundSteps = 1,
                stepWaitTicks = 2,
            )
        }

        assertVec3Equals(movement, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)

        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
        assertVec3Equals(movement, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `replacement outbound retains exact inverse of every confirmed movement`() {
        val first = Vec3(2.0, 0.0, 0.0)
        val replacement = Vec3(5.0, 0.0, 0.0)
        val session = SpearKillPacketSessionState().apply {
            start(
                path = listOf(first, Vec3(1.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3.ZERO),
                outboundSteps = 2,
            )
        }

        assertVec3Equals(first, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertTrue(session.replaceRemainingOutbound(listOf(replacement), strikeHoldTicks = 0))

        val expectedSteps = listOf(
            replacement to first.add(replacement),
            replacement.scale(-1.0) to first,
            first.scale(-1.0) to Vec3.ZERO,
        )
        expectedSteps.forEach { (expectedMovement, expectedOffset) ->
            assertVec3Equals(expectedOffset, session.prepareNextStep()!!, 1e-9)
            assertVec3Equals(expectedMovement, session.pendingMovement!!, 1e-9)
            session.confirmStep(delivered = true)
        }

        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
        assertNull(session.prepareNextStep())
        assertFalse(session.active)
    }
}
