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

class SpearKillVirtualPositionUsesPhysicalGroundProximityTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `virtual position uses physical ground proximity even during horizontal movement`() {
        val packet = ServerboundMovePlayerPacket.PosRot(
            10.0,
            20.0,
            30.0,
            45f,
            -20f,
            true,
            true,
        )

        applySpearKillVirtualPosition(
            packet,
            Vec3(10.0, 20.0, 30.0),
            Vec3(4.0, 0.0, 2.0),
            grounded = false,
        )

        assertFalse(packet.isOnGround)
    }

    @Test
    fun `virtual movement applies a heading aligned with the kinetic motion`() {
        val movement = Vec3(2.0, -1.0, 3.0)
        val heading = spearKillKineticHeading(movement)!!
        val packet = ServerboundMovePlayerPacket.StatusOnly(false, false)

        applySpearKillVirtualPosition(
            packet = packet,
            playerPosition = Vec3(10.0, 20.0, 30.0),
            virtualOffset = movement,
            heading = heading,
        )

        assertVec3Equals(movement.normalize(), heading.directionVector, 1e-3)
        assertTrue(packet.hasRot)
        assertEquals(heading.yaw, packet.yRot)
        assertEquals(heading.pitch, packet.xRot)
        assertEquals(Rotation.fromRotationVec(movement), heading)
    }

    @Test
    fun `Packet path heading follows every XYZ step and overrides ambient camera rotation`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                Vec3(3.0, 0.0, 0.0),
                Vec3(0.0, 2.0, 3.0),
                Vec3(0.0, -2.0, -3.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            stepWaitTicks = 1,
        )

        assertEquals(Rotation.fromRotationVec(Vec3(3.0, 0.0, 0.0)), session.pathHeading)
        session.prepareNextStep()
        val forwardHeading = session.pathHeading!!
        assertEquals(Rotation.fromRotationVec(Vec3(3.0, 0.0, 0.0)), forwardHeading)
        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, 2.0, 3.0)), session.pathHeading)

        session.prepareNextStep()
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, 2.0, 3.0)), session.pathHeading)
        val ambientPacket = ServerboundMovePlayerPacket.Rot(140f, -70f, false, false)
        applySpearKillPathHeading(ambientPacket, session.pathHeading)
        assertEquals(session.pathHeading!!.yaw, ambientPacket.yRot)
        assertEquals(session.pathHeading!!.pitch, ambientPacket.xRot)
        assertTrue(ambientPacket.hasRot)

        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        session.prepareNextStep()
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, -2.0, -3.0)), session.pathHeading)
    }

    @Test
    fun `Packet pre-strike hold locks rotation onto the terminal lunge`() {
        val approachMovement = Vec3(0.0, 0.0, 4.0)
        val terminalMovement = Vec3(4.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        session.start(
            path = listOf(
                approachMovement,
                terminalMovement,
                terminalMovement.scale(-1.0),
                approachMovement.scale(-1.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 1,
        )

        assertEquals(Rotation.fromRotationVec(approachMovement), session.pathHeading)
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertEquals(Rotation.fromRotationVec(terminalMovement), session.pathHeading)
        assertNull(session.prepareNextStep())
        assertEquals(Rotation.fromRotationVec(terminalMovement), session.pathHeading)
    }

    @Test
    fun `route rotation override is instant silent and persistent for one tick`() {
        val heading = Rotation(65f, -12f)
        val target = spearKillRouteRotationTarget(heading)

        assertEquals(heading, target.rotation)
        assertTrue(target.processors.isEmpty())
        assertEquals(1, target.ticksUntilReset)
        assertFalse(target.considerInventory)
        assertEquals(MovementCorrection.OFF, target.movementCorrection)
    }

    @Test
    fun `terminal attack ray follows the serverbound kinetic movement`() {
        val eye = Vec3(0.0, 65.5, 0.0)
        val targetBox = AABB(4.0, 64.0, -0.5, 5.0, 66.0, 0.5)

        assertTrue(findSpearKillTerminalAttackHitPoint(
            eye = eye,
            terminalMovement = Vec3(7.0, 0.0, 0.0),
            targetBox = targetBox,
            range = 7.0,
        ) != null)
        assertNull(findSpearKillTerminalAttackHitPoint(
            eye = eye,
            terminalMovement = Vec3(0.0, 0.0, 7.0),
            targetBox = targetBox,
            range = 7.0,
        ))
    }
}
