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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar

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

class SpearKillAStarReplanningFreezesBeforeTerminalSuffixTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `AStar replanning freezes before terminal suffix begins`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 4,
            terminalSuffixSteps = 3,
        )

        assertTrue(session.canReplaceRemainingApproach)
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertFalse(session.canReplaceRemainingApproach)
    }

    @Test
    fun `AStar replanning waits until the current packet cadence is ready`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 3,
            stepWaitTicks = 2,
            terminalSuffixSteps = 1,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertFalse(session.canReplaceRemainingApproach)
        assertNull(session.prepareNextStep())
        assertFalse(session.canReplaceRemainingApproach)
        assertNull(session.prepareNextStep())
        assertTrue(session.canReplaceRemainingApproach)
    }

    @Test
    fun `AStar replan applies pre-hold and terminal suffix count to the replacement outbound`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
            ),
            strikeHoldTicks = 2,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 2,
        ))

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertVec3Equals(Vec3(6.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(9.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar keeps its strike hold ahead of the configured step wait`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(Vec3(3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 2,
            stepWaitTicks = 2,
        )

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertFalse(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `NetworkOptimized immediate return retains its configured packet cadence`() {
        val outbound = Vec3(3.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(outbound, outbound.scale(-1.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 0,
            stepWaitTicks = 2,
        )

        assertVec3Equals(outbound, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        repeat(2) {
            assertNull(session.prepareNextStep())
            assertFalse(session.holdingStrike)
        }
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(outbound.scale(-1.0), session.pendingMovement!!, 1e-9)
    }

    @Test
    fun `NetworkOptimized exact abort returns on the next cadence slot`() {
        val first = Vec3(3.0, 0.0, 0.0)
        val second = Vec3(2.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
            outboundSteps = 2,
            strikeHoldTicks = 0,
            stepWaitTicks = 2,
        )

        assertVec3Equals(first, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        session.beginExactReturn()

        repeat(2) {
            assertNull(session.prepareNextStep())
            assertFalse(session.holdingStrike)
        }
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(first.scale(-1.0), session.pendingMovement!!, 1e-9)
    }

    @Test
    fun `every Packet strike hold suppresses ambient movement and tick end kinetic resets`() {
        assertTrue(shouldSuppressSpearKillStrikeHoldPacket(holdingStrike = true))
        assertFalse(shouldSuppressSpearKillStrikeHoldPacket(holdingStrike = false))
        assertTrue(shouldSuppressSpearKillKineticResetPacket(
            holdingStrike = true,
            clientTickEndPacket = true,
        ))
        assertFalse(shouldSuppressSpearKillKineticResetPacket(
            holdingStrike = false,
            clientTickEndPacket = true,
        ))
        assertFalse(shouldSuppressSpearKillKineticResetPacket(
            holdingStrike = true,
            clientTickEndPacket = false,
        ))
    }

    @Test
    fun `cancelled final AStar outbound step retries before the strike hold`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 1,
        )

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar recovery clears a pending strike hold`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginRecovery(maxSpeed = 4.0)

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }
}
