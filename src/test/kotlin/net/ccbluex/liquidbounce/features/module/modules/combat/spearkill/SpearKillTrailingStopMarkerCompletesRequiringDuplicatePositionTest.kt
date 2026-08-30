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

class SpearKillTrailingStopMarkerCompletesRequiringDuplicatePositionTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `trailing stop marker completes without requiring a duplicate position packet`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO))

        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }

        assertFalse(session.active)
        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `aborted packet path recovers with bounded steps`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(listOf(Vec3(10.0, -4.0, 0.0), Vec3(-10.0, 4.0, 0.0)))
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginRecovery(maxSpeed = 4.0)

        assertTrue(session.recovering)
        while (session.active) {
            val before = session.committedOffset
            val next = session.prepareNextStep()!!
            assertTrue(next.subtract(before).length() <= 4.0)
            session.confirmStep(delivered = true)
        }

        assertFalse(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `server setback replaces stale path and recovers with bounded steps`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)))
        session.prepareNextStep()

        session.beginRecoveryFrom(Vec3(18.0, -4.0, 0.0), maxSpeed = 5.0)

        assertTrue(session.recovering)
        assertVec3Equals(Vec3(18.0, -4.0, 0.0), session.committedOffset, 1e-9)
        while (session.active) {
            val before = session.committedOffset
            val next = session.prepareNextStep()!!
            assertTrue(next.subtract(before).length() <= 5.0)
            session.confirmStep(delivered = true)
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `setback can reuse the exact collision-safe inverse of confirmed XYZ movement`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val first = Vec3(5.0, 3.0, 0.0)
        val second = Vec3(0.0, 0.0, 4.0)
        session.startPhysicalReturn(
            path = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
            outboundSteps = 2,
        )
        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
            session.consumePhysicalPositionOffset()
        }
        val authoritativeOffset = first.add(second)
        val exactReturn = session.exactRecoveryMovementsFrom(authoritativeOffset)!!

        assertEquals(listOf(second.scale(-1.0), first.scale(-1.0)), exactReturn)

        session.beginPhysicalExactRecoveryFrom(authoritativeOffset, exactReturn)
        val confirmedOffsets = mutableListOf(session.consumePhysicalPositionOffset()!!)
        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()?.let(confirmedOffsets::add)
        }

        assertEquals(listOf(authoritativeOffset, first, Vec3.ZERO), confirmedOffsets)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `late setback to a delivered virtual position restores the local state`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(
            Vec3(10.0, 64.0, 2.0),
            Vec3(0.2, -0.1, 0.3),
            45f,
            -20f,
        )
        guard.record(Vec3(18.0, 60.0, 2.0), localState.position)
        guard.tick(pathActive = false)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                7,
                PositionMoveRotation(Vec3(18.0, 60.0, 2.0), Vec3.ZERO, 90f, 30f),
                emptySet(),
            ),
        )

        assertEquals(localState, restore)
    }

    @Test
    fun `server rejection to the route origin stops the stale packet path`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(
            Vec3(10.0, 64.0, 2.0),
            Vec3.ZERO,
            0f,
            0f,
        )
        guard.record(Vec3(27.32, 64.0, 2.0), localState.position)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                12,
                PositionMoveRotation(localState.position, Vec3.ZERO, 0f, 0f),
                emptySet(),
            ),
        )

        assertEquals(localState, restore)
    }

    @Test
    fun `relative setback coordinates match a delivered virtual position`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(Vec3(10.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f)
        guard.record(Vec3(14.0, 64.0, 2.0), localState.position)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                8,
                PositionMoveRotation(Vec3(4.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f),
                setOf(Relative.X),
            ),
        )

        assertEquals(localState, restore)
    }

    @Test
    fun `only the marked correction packet completes a rollback`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val rollback = SpearKillSetbackRollback()
        val localState = SpearKillLocalPlayerState(
            movement = PositionMoveRotation(Vec3(10.0, 64.0, 2.0), Vec3.ZERO, 15f, -5f),
            oldPosition = Vec3(9.8, 64.0, 2.0),
            oldYRot = 14f,
            oldXRot = -4f,
        )
        val marked = ClientboundPlayerPositionPacket(
            10,
            PositionMoveRotation(Vec3(18.0, 60.0, 2.0), Vec3.ZERO, 90f, 30f),
            emptySet(),
        )
        val unrelated = ClientboundPlayerPositionPacket(
            11,
            marked.change,
            marked.relatives,
        )
        guard.record(marked.change.position, localState.movement.position)
        rollback.mark(marked)

        assertEquals(null, rollback.prepare(unrelated, localState, guard))
        val prepared = rollback.prepare(marked, localState, guard)
        assertVec3Equals(Vec3(8.0, -4.0, 0.0), prepared!!.authoritativeOffset, 1e-9)
        assertEquals(null, rollback.finish(unrelated))
        assertEquals(prepared, rollback.finish(marked))
        assertEquals(null, rollback.finish(marked))
    }
}
