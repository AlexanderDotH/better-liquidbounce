/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.ShapeFlag
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import net.ccbluex.liquidbounce.utils.block.aStarShortestPath
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

class SpearKillSelectedRoundTripRejectedBeforeEmissionWhenTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `selected round trip is rejected before emission when one server step clips`() {
        val origin = Vec3(10.0, 64.0, 2.0)
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(Vec3(17.32, 0.0, 0.0)),
            roundTripMovements = listOf(
                Vec3(17.32, 0.0, 0.0),
                Vec3(-17.32, 0.0, 0.0),
                Vec3.ZERO,
            ),
        )
        val validator = SpearKillAStarSegmentValidator { from, to ->
            to.subtract(from).x <= 17.0
        }

        assertFalse(isSpearKillPacketRouteServerAccepted(origin, route, validator))
    }

    @Test
    fun `unsafe pending outbound step returns along only confirmed movement`() {
        val firstMovement = Vec3(2.0, 1.0, 0.0)
        val unsafeMovement = Vec3(4.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(
                firstMovement,
                unsafeMovement,
                unsafeMovement.scale(-1.0),
                firstMovement.scale(-1.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )

        assertVec3Equals(firstMovement, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        val unsafeOffset = firstMovement.add(unsafeMovement)
        assertVec3Equals(unsafeOffset, session.prepareNextStep()!!, 1e-9)
        assertFalse(isSpearKillPacketStepClear(
            sessionOrigin = Vec3.ZERO,
            committedOffset = firstMovement,
            candidateOffset = unsafeOffset,
            maxStepLength = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
        ))

        session.confirmStep(delivered = false)
        session.beginExactReturn()

        assertTrue(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(firstMovement.scale(-1.0), session.pendingMovement!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
    }

    @Test
    fun `AStar refuses to replace a pending or returning packet path`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
        )

        session.prepareNextStep()
        assertFalse(session.canReplaceRemainingOutbound)
        assertFalse(session.replaceRemainingOutbound(listOf(Vec3(1.0, 0.0, 0.0)), 0))
        session.confirmStep(delivered = true)
        assertFalse(session.canReplaceRemainingOutbound)
        assertFalse(session.replaceRemainingOutbound(listOf(Vec3(1.0, 0.0, 0.0)), 0))
    }

    @Test
    fun `AStar strike hold waits after the final outbound step before returning`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            strikeHoldTicks = 2,
        )

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertTrue(session.active)
        assertTrue(session.holdingStrike)
        assertEquals(null, session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertEquals(null, session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        assertFalse(session.holdingStrike)
    }

    @Test
    fun `direct Packet live-locks its terminal motion before immediate exact return`() {
        val outbound = Vec3(6.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(outbound),
            roundTripMovements = listOf(outbound, outbound.scale(-1.0), Vec3.ZERO),
        )

        startSpearKillDirectPacketSession(
            session = session,
            route = route,
            stepWaitTicks = 0,
            strikeHoldTicks = 0,
        )

        assertTrue(session.awaitingTerminalCommitAuthorization)
        assertFalse(session.terminalAimLockComplete)
        assertNull(session.prepareNextStep())
        assertTrue(session.terminalAimLockComplete)
        assertNull(session.prepareNextStep())
        assertTrue(session.authorizeTerminalCommit())
        assertVec3Equals(outbound, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(outbound.scale(-1.0), session.pendingMovement!!, 1e-9)
        assertFalse(session.holdingStrike)
    }

    @Test
    fun `AStar rejects waits longer than its one aim-lock tick`() {
        val terminal = Vec3(3.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())

        assertThrows<IllegalArgumentException> {
            session.start(
                path = listOf(terminal, terminal.scale(-1.0), Vec3.ZERO),
                outboundSteps = 1,
                preStrikeHoldTicks = 2,
                terminalSuffixSteps = 1,
                requireTerminalAuthorization = true,
            )
        }
    }

    @Test
    fun `AStar isolates its terminal lunge behind a one-tick movement barrier`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                Vec3(-4.0, 0.0, 0.0),
                Vec3(7.0, 0.0, 0.0),
                Vec3(-7.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 1,
        )

        assertVec3Equals(Vec3(-4.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        assertFalse(session.holdingKineticBarrier)
    }
}
