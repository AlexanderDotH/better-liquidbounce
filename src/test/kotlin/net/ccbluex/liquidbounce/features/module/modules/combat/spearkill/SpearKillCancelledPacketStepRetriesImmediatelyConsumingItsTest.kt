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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.spearKillPacketTravelTicks
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

class SpearKillCancelledPacketStepRetriesImmediatelyConsumingItsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `cancelled Packet step retries immediately without consuming its configured wait`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(Vec3(3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3.ZERO),
            stepWaitTicks = 4,
        )

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `Packet target prediction includes inter-step wait time`() {
        assertEquals(4, spearKillPacketTravelTicks(stepCount = 4, stepWaitTicks = 0))
        assertEquals(10, spearKillPacketTravelTicks(stepCount = 4, stepWaitTicks = 2))
    }

    @Test
    fun `direct Packet prediction includes live aim lock without a return hold`() {
        assertEquals(1, spearKillDirectPacketHitTicks(
            stepCount = 1,
            stepWaitTicks = 0,
            strikeHoldTicks = 0,
        ))
        assertEquals(10, spearKillDirectPacketHitTicks(
            stepCount = 4,
            stepWaitTicks = 2,
            strikeHoldTicks = 0,
        ))
        assertEquals(3, spearKillDirectPacketHitTicks(
            stepCount = 1,
            stepWaitTicks = 0,
            strikeHoldTicks = SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS,
        ))
    }

    @Test
    fun `direct Packet start waits for a damage window that reaches the server hit tick`() {
        assertTrue(hasSpearKillDirectPacketDamageWindow(
            ticksUsingItem = 9,
            damageUseDuration = 10,
            stepCount = 1,
            stepWaitTicks = 0,
        ))
        assertFalse(hasSpearKillDirectPacketDamageWindow(
            ticksUsingItem = 10,
            damageUseDuration = 10,
            stepCount = 1,
            stepWaitTicks = 0,
        ))
    }

    @Test
    fun `paced Packet route may start when a fresh terminal charge still reaches the hit`() {
        assertFalse(hasSpearKillDirectPacketDamageWindow(
            ticksUsingItem = 6,
            damageUseDuration = 10,
            stepCount = 12,
            stepWaitTicks = 0,
        ))
        assertTrue(hasSpearKillRefreshableTerminalDamageWindow(
            delayTicks = 5,
            damageUseDuration = 10,
            terminalStepCount = 1,
            stepWaitTicks = 0,
        ))
    }

    @Test
    fun `AStar prediction includes every shared Packet wait`() {
        assertEquals(4, spearKillAStarPredictionTicks(distance = 28.0, maxSpeed = 7.0, stepWaitTicks = 0))
        assertEquals(10, spearKillAStarPredictionTicks(distance = 28.0, maxSpeed = 7.0, stepWaitTicks = 2))
    }

    @Test
    fun `AStar arrival prediction uses the actual route and pre-strike barrier`() {
        assertEquals(10, spearKillAStarArrivalTicks(
            outboundStepCount = 8,
            stepWaitTicks = 0,
            preStrikeHoldTicks = 2,
        ))
        assertEquals(17, spearKillAStarArrivalTicks(
            outboundStepCount = 6,
            stepWaitTicks = 2,
            preStrikeHoldTicks = 1,
        ))
    }

    @Test
    fun `path schedule rejects waits beyond one aim-lock tick`() {
        assertNull(buildSpearKillPathSchedule(
            outboundStepCount = 6,
            stepWaitTicks = 1,
            terminalSuffixCount = 3,
            preStrikeHoldTicks = 2,
            strikeHoldTicks = 2,
        ))
    }

    @Test
    fun `candidate lower bound includes approach terminal packets waits and strike hold`() {
        assertEquals(
            7,
            spearKillAStarCandidateLowerBoundHitTick(
                routeOrigin = Vec3.ZERO,
                plannerGoal = Vec3(20.0, 0.0, 0.0),
                stepLimit = 10.0,
                terminalLungeDistance = 10.0,
                stepWaitTicks = 1,
                strikeHoldTicks = 2,
            ),
        )
    }

    @Test
    fun `approach refinement reacts only to horizontal seed drift over half a block`() {
        val seed = Vec3(5.0, 64.0, 5.0)

        assertFalse(shouldRefineSpearKillAStarApproach(seed, seed.add(0.5, 10.0, 0.0)))
        assertTrue(shouldRefineSpearKillAStarApproach(seed, seed.add(0.51, 0.0, 0.0)))
    }

    @Test
    fun `AStar schedule uses one aim-lock tick without predictive waiting`() {
        val schedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = 3,
            stepWaitTicks = 0,
            terminalSuffixCount = 1,
            strikeHoldTicks = 2,
        )!!

        assertEquals(listOf(0, 1, 3), schedule.stepStartTicks)
        assertEquals(3, schedule.terminalStartTick)
        assertEquals(5, schedule.hitTick)
    }

    @Test
    fun `terminal suffix count matches trailing MaxSpeed corridor packets`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3(0.0, 64.0, 0.0),
            terminalWaypoint = Vec3(10.0, 64.0, 0.0),
        )
        val outbound = listOf(
            Vec3(2.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(4, countSpearKillAStarTerminalSuffix(outbound, approach, stepLimit = 3.0))
        assertNull(countSpearKillAStarTerminalSuffix(outbound.dropLast(1), approach, stepLimit = 3.0))
    }

    @Test
    fun `schedule damage window gates on hitTick`() {
        assertTrue(hasSpearKillScheduleDamageWindow(
            ticksUsingItem = 10,
            damageUseDuration = 40,
            hitTick = 30,
        ))
        assertFalse(hasSpearKillScheduleDamageWindow(
            ticksUsingItem = 20,
            damageUseDuration = 40,
            hitTick = 21,
        ))
        assertTrue(hasSpearKillAStarDamageWindow(
            ticksUsingItem = 10,
            damageUseDuration = 40,
            outboundStepCount = 4,
            stepWaitTicks = 0,
            confirmationTicks = 2,
            preStrikeHoldTicks = 0,
            terminalSuffixCount = 1,
        ))
    }
}
