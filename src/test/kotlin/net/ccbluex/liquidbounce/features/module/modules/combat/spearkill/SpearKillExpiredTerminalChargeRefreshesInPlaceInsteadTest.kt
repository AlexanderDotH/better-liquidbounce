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

class SpearKillExpiredTerminalChargeRefreshesInPlaceInsteadTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `expired terminal charge refreshes in place instead of stalling the lunge`() {
        assertEquals(
            SpearKillTerminalChargeAction.REFRESH,
            resolveSpearKillTerminalChargeAction(
                isUsingSpear = true,
                ticksUsingItem = 18,
                delayTicks = 5,
                damageUseDuration = 20,
                remainingHitTicks = 4,
            ),
        )
        assertEquals(
            SpearKillTerminalChargeAction.WAIT,
            resolveSpearKillTerminalChargeAction(
                isUsingSpear = true,
                ticksUsingItem = 5,
                delayTicks = 5,
                damageUseDuration = 20,
                remainingHitTicks = 4,
            ),
        )
        assertEquals(
            SpearKillTerminalChargeAction.READY,
            resolveSpearKillTerminalChargeAction(
                isUsingSpear = true,
                ticksUsingItem = 8,
                delayTicks = 5,
                damageUseDuration = 20,
                remainingHitTicks = 4,
            ),
        )
        assertEquals(
            SpearKillTerminalChargeAction.INVALID,
            resolveSpearKillTerminalChargeAction(
                isUsingSpear = true,
                ticksUsingItem = 5,
                delayTicks = 5,
                damageUseDuration = 8,
                remainingHitTicks = 3,
            ),
        )
    }

    @Test
    fun `terminal commit aim must point at the predicted target center`() {
        val eye = Vec3(0.0, 65.5, 0.0)
        val movement = Vec3(7.0, 0.0, 0.0)

        assertTrue(isSpearKillTerminalAimAligned(
            eye = eye,
            terminalMovement = movement,
            targetPoint = Vec3(2.5, 65.5, 0.0),
        ))
        assertFalse(isSpearKillTerminalAimAligned(
            eye = eye,
            terminalMovement = movement,
            targetPoint = Vec3(2.5, 65.5, 0.2),
        ))
    }

    @Test
    fun `timed plan selection prefers earlier hits then fewer outbound steps`() {
        assertTrue(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 10,
            candidateOutboundSteps = 8,
            bestHitTick = 12,
            bestOutboundSteps = 4,
        ))
        assertFalse(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 12,
            candidateOutboundSteps = 3,
            bestHitTick = 10,
            bestOutboundSteps = 8,
        ))
        assertTrue(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 10,
            candidateOutboundSteps = 3,
            bestHitTick = 10,
            bestOutboundSteps = 5,
        ))
    }

    @Test
    fun `AStar waits for a fresh spear use window when its route would expire before impact`() {
        assertTrue(hasSpearKillDamageWindow(
            ticksUsingItem = 20,
            damageUseDuration = 80,
            arrivalTicks = 30,
            confirmationTicks = 2,
        ))
        assertFalse(hasSpearKillDamageWindow(
            ticksUsingItem = 60,
            damageUseDuration = 80,
            arrivalTicks = 20,
            confirmationTicks = 2,
        ))
    }

    @Test
    fun `AStar replaces only unconfirmed outbound tail and still returns exactly to origin`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 1.0, 0.0),
                Vec3(2.0, 1.0, 0.0),
                Vec3(-2.0, -1.0, 0.0),
                Vec3(-2.0, -1.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertTrue(session.canReplaceRemainingOutbound)
        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(Vec3(0.0, 2.0, 2.0), Vec3(1.0, 0.0, 1.0)),
            strikeHoldTicks = 0,
        ))
        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(Vec3(-1.0, 1.0, 2.0), Vec3(0.0, 1.0, 1.0)),
            strikeHoldTicks = 0,
        ))

        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `defeated Packet target can chain from its endpoint and still return to the first origin`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            strikeHoldTicks = 2,
        )
        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }

        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(
                Vec3(0.0, 4.0, 0.0),
                Vec3(0.0, 1.0, 0.0),
            ),
            strikeHoldTicks = 0,
        ))
        assertFalse(session.recovering)

        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }
        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(Vec3(0.0, 0.0, 3.0)),
            strikeHoldTicks = 0,
        ))

        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }
}
