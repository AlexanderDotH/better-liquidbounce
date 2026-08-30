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

class SpearKillTargetSelectionAcceptsEntityJustInsideWidenedTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `target selection accepts an entity just inside the widened three dimensional ray`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)
        val entityBox = AABB(4.0, 0.0, 0.30, 4.6, 1.8, 0.90)

        assertFalse(entityBox.clip(eye, lookEnd).isPresent)
        val priority = spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35)!!

        assertFalse(priority.directlyHovered)
    }

    @Test
    fun `target selection rejects an entity beyond the widened ray margin`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)
        val entityBox = AABB(4.0, 0.0, 0.36, 4.6, 1.8, 0.96)

        assertNull(spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35))
    }

    @Test
    fun `target selection uses a fixed hitbox pad not a distance-scaled cone`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(50.0, 1.6, 0.0)
        val margin = spearKillTargetSelectionMargin()
        // Slightly outside the vanilla box, still inside the fixed pad.
        val insidePad = AABB(20.0, 0.0, 0.40, 20.6, 1.8, 1.00)
        val outsidePad = AABB(20.0, 0.0, 0.90, 20.6, 1.8, 1.50)

        assertEquals(0.75, margin, 1e-9)
        assertEquals(margin, spearKillTargetSelectionMargin())
        assertTrue(spearKillLookRayPriority(insidePad, eye, lookEnd, hitboxMargin = margin) != null)
        assertNull(spearKillLookRayPriority(outsidePad, eye, lookEnd, hitboxMargin = margin))
    }

    @Test
    fun `AStar through-terrain ranking prefers the aimed far target over a near interceptor`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(40.0, 1.6, 0.0)
        val nearerInterceptor = AABB(5.0, 0.0, -0.3, 5.6, 1.8, 0.3)
        val farTarget = AABB(30.0, 0.0, -0.25, 30.6, 1.8, 0.25)

        val nearPriority = spearKillLookRayPriority(nearerInterceptor, eye, lookEnd)!!
        val farPriority = spearKillLookRayPriority(farTarget, eye, lookEnd)!!

        assertTrue(nearPriority < farPriority)
        assertTrue(
            compareSpearKillLookRayPriority(
                left = farPriority,
                right = nearPriority,
                throughTerrain = true,
            ) < 0,
        )
    }

    @Test
    fun `AStar route start rejects missing paths and blocks impossible terminal windows`() {
        assertEquals(
            SpearKillAttackStartResult.REJECTED,
            classifySpearKillAStarStartFailure(
                routeFound = false,
                hasRefreshableTerminalDamageWindow = true,
            ),
        )
        assertEquals(
            SpearKillAttackStartResult.BLOCKED,
            classifySpearKillAStarStartFailure(
                routeFound = true,
                hasRefreshableTerminalDamageWindow = false,
            ),
        )
        assertEquals(
            SpearKillAttackStartResult.STARTED,
            classifySpearKillAStarStartFailure(
                routeFound = true,
                hasRefreshableTerminalDamageWindow = true,
            ),
        )
        assertEquals(
            SpearKillAttackStartResult.BLOCKED,
            classifySpearKillAStarStartFailure(
                routeFound = true,
                hasRefreshableTerminalDamageWindow = true,
                serverRouteAccepted = false,
            ),
        )
    }

    @Test
    fun `target selection rejects vertical separation outside the widened margin`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)

        listOf(
            AABB(4.0, 20.0, -0.3, 4.6, 21.8, 0.3),
            AABB(4.0, -20.0, -0.3, 4.6, -18.2, 0.3),
        ).forEach { entityBox ->
            assertNull(spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35))
        }
    }

    @Test
    fun `crosshair aligned target outranks a nearer target inside the look tolerance`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val nearerOffAxis = AABB(3.0, 0.0, 0.30, 3.6, 1.8, 0.90)
        val fartherAligned = AABB(8.0, 0.0, -0.3, 8.6, 1.8, 0.3)

        val offAxisPriority = spearKillLookRayPriority(nearerOffAxis, eye, lookEnd, hitboxMargin = 0.35)!!
        val alignedPriority = spearKillLookRayPriority(fartherAligned, eye, lookEnd, hitboxMargin = 0.35)!!

        assertFalse(offAxisPriority.directlyHovered)
        assertTrue(alignedPriority.directlyHovered)
        assertTrue(alignedPriority < offAxisPriority)
    }

    @Test
    fun `crosshair ray follows pitch and rejects a target at the wrong elevation`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 11.6, 0.0)
        val nearerSameYaw = AABB(3.0, 0.0, -0.3, 3.6, 1.8, 0.3)
        val hoveredHigherTarget = AABB(8.0, 5.3, -0.3, 8.6, 7.1, 0.3)

        val hoveredPriority = spearKillLookRayPriority(
            hoveredHigherTarget,
            eye,
            lookEnd,
            hitboxMargin = 0.35,
        )!!

        assertNull(spearKillLookRayPriority(nearerSameYaw, eye, lookEnd, hitboxMargin = 0.35))
        assertTrue(hoveredPriority.directlyHovered)
    }

    @Test
    fun `equally aligned targets use their first look ray hit as tie breaker`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val nearerAligned = AABB(3.0, 0.0, -0.3, 3.6, 1.8, 0.3)
        val fartherAligned = AABB(8.0, 0.0, -0.3, 8.6, 1.8, 0.3)

        val nearerPriority = spearKillLookRayPriority(nearerAligned, eye, lookEnd)!!
        val fartherPriority = spearKillLookRayPriority(fartherAligned, eye, lookEnd)!!

        assertTrue(nearerPriority < fartherPriority)
    }

    @Test
    fun `widened crosshair ray has a bounded vertical tolerance`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val insideMargin = AABB(6.0, 1.90, -0.2, 6.6, 3.7, 0.2)
        val outsideMargin = AABB(6.0, 1.96, -0.2, 6.6, 3.8, 0.2)

        assertTrue(spearKillLookRayPriority(insideMargin, eye, lookEnd, hitboxMargin = 0.35) != null)
        assertNull(spearKillLookRayPriority(outsideMargin, eye, lookEnd, hitboxMargin = 0.35))
    }

    @Test
    fun `attack ray uses the aimed hitbox edge instead of its center`() {
        val eye = Vec3(0.0, 1.6, 0.2)
        val targetBox = AABB(4.0, 1.0, 0.0, 5.0, 2.0, 1.0)

        val hit = findSpearKillAttackHitPoint(
            eye = eye,
            direction = Vec3(1.0, 0.0, 0.0),
            targetBox = targetBox,
            range = 10.0,
        )!!

        assertVec3Equals(Vec3(4.0, 1.6, 0.2), hit, 1e-9)
        assertFalse(hit == targetBox.center)
    }
}
