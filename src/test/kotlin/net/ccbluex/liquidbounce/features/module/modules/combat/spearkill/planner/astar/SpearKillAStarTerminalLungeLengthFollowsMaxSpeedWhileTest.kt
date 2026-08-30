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

class SpearKillAStarTerminalLungeLengthFollowsMaxSpeedWhileTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `AStar terminal lunge length follows MaxSpeed while StepLimit only chunks packets`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 10.0,
        )!!
        val route = buildSpearKillAStarPacketRoute(
            origin = approach.plannerGoal,
            outboundWaypoints = listOf(approach.terminalWaypoint),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(10.0, approach.plannerGoal.distanceTo(approach.terminalWaypoint), 1e-9)
        assertEquals(
            listOf(
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
            ),
            route.outboundMovements,
        )
        assertTrue(isSpearKillAStarTerminalStepValid(route.outboundMovements, approach, stepLimit = 3.0))
        assertVec3Equals(
            approach.terminalWaypoint.subtract(approach.plannerGoal),
            route.outboundMovements.fold(Vec3.ZERO, Vec3::add),
            1e-9,
        )
    }

    @Test
    fun `AStar attack approach offers lateral long-lunge alternatives`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertTrue(approaches.size >= 3)
        assertTrue(approaches.first().terminalWaypoint.subtract(approaches.first().plannerGoal).x > 0.0)
        assertTrue(approaches.drop(1).any { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).z != 0.0
        })
        assertTrue(approaches.all { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).y == 0.0
        })
    }
}
