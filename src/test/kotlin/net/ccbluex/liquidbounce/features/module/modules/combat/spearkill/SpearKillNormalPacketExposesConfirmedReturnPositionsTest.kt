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

class SpearKillNormalPacketExposesConfirmedReturnPositionsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `normal packet exposes only confirmed return positions`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(2.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertEquals(null, session.consumePhysicalPositionOffset())

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertTrue(session.recovering)
        assertVec3Equals(Vec3(4.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)
        assertEquals(null, session.consumePhysicalPositionOffset())
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
    }

    @Test
    fun `aborted normal packet exposes its delivered endpoint and exact return`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        val first = Vec3(3.0, 0.0, 0.0)
        val second = Vec3(0.0, 0.0, 2.0)
        session.startPhysicalReturn(
            path = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginExactReturn()

        assertTrue(session.recovering)
        assertVec3Equals(first, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
    }

    @Test
    fun `AStar packet exposes its endpoint and confirmed return after the strike hold`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.startPhysicalReturn(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 1,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)

        assertFalse(session.active)
    }

    @Test
    fun `normal setback recovery exposes authoritative offset and bounded return`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())

        session.beginPhysicalRecoveryFrom(
            authoritativeOffset = Vec3(9.0, 0.0, 0.0),
            maxSpeed = 4.0,
        )

        assertVec3Equals(Vec3(9.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)
        while (session.active) {
            val before = session.committedOffset
            session.prepareNextStep()
            session.confirmStep(delivered = true)
            val physicalOffset = session.consumePhysicalPositionOffset()!!
            assertTrue(physicalOffset.subtract(before).length() <= 4.0)
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `return stays packet only when the client is still at the session origin`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 0.0, 0.0)))
        assertEquals(
            null,
            positioner.resolve(origin, origin, Vec3(4.0, 0.0, 0.0)),
        )
        assertEquals(null, positioner.resolve(origin, origin, Vec3.ZERO))
    }

    @Test
    fun `ordinary horizontal and vertical drift does not turn a packet return physical`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(
            null,
            positioner.resolve(origin, origin.add(1.4, 0.6, 0.8), Vec3(8.0, 4.0, 0.0)),
        )
        assertEquals(
            null,
            positioner.resolve(origin, origin.add(2.0, 0.0, 0.0), Vec3(4.0, 2.0, 0.0)),
        )
    }

    @Test
    fun `physical packet return begins only at a confirmed route position`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertVec3Equals(
            origin.add(4.0, 2.0, 0.0),
            positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
            1e-9,
        )
    }

    @Test
    fun `displaced client follows confirmed absolute return positions back to origin`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertVec3Equals(
            origin.add(8.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(
            origin.add(4.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(origin, positioner.resolve(origin, origin.add(4.0, 0.0, 0.0), Vec3.ZERO)!!, 1e-9)
    }

    @Test
    fun `new return clears the previous packet only decision`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 0.0, 0.0)))
        positioner.clear()

        assertVec3Equals(
            origin.add(8.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0))!!,
            1e-9,
        )
    }

    @Test
    fun `late displacement upgrades a packet-only return to physical position updates`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 4.0, 0.0)))
        assertVec3Equals(
            origin.add(4.0, 2.0, 0.0),
            positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(origin, positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3.ZERO)!!, 1e-9)
    }
}
