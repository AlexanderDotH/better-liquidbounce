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

class SpearKillAStarTargetEligibilityBypassesDirectGatesTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `AStar target eligibility bypasses direct gates only when enabled`() {
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = false,
            packetAStarEnabled = false,
        ))
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = false,
            packetAStarEnabled = true,
        ))
        // Packet non-A* remains selectable on LOS; its route preflight owns body-corridor rejection.
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = false,
            packetAStarEnabled = false,
            packetMovementMode = true,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
            packetMovementMode = true,
        ))
    }

    @Test
    fun `dead Packet target is defeated before removal world and range failures`() {
        assertEquals(
            SpearKillPacketTargetState.DEFEATED,
            classifySpearKillPacketTargetState(
                isAlive = false,
                isRemoved = true,
                isInCurrentWorld = false,
                isWithinRange = false,
            ),
        )
    }

    @Test
    fun `alive invalid Packet targets are unreachable`() {
        listOf(
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = true,
                isInCurrentWorld = true,
                isWithinRange = true,
            ),
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = false,
                isWithinRange = true,
            ),
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = true,
                isWithinRange = false,
            ),
        ).forEach { state ->
            assertEquals(SpearKillPacketTargetState.UNREACHABLE, state)
        }

        assertEquals(
            SpearKillPacketTargetState.ACTIVE,
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = true,
                isWithinRange = true,
            ),
        )
    }

    @Test
    fun `held undercharged spear waits for vanilla charge cadence without packet acceleration`() {
        assertEquals(
            SpearKillChargeDecision.WAIT_FOR_VANILLA,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 2,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = true,
            ),
        )
    }

    @Test
    fun `interrupted undercharged spear releases SpearKill ownership`() {
        assertEquals(
            SpearKillChargeDecision.RESET,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 2,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = false,
            ),
        )
    }

    @Test
    fun `charged spear continues to route planning`() {
        assertEquals(
            SpearKillChargeDecision.READY,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 3,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = true,
            ),
        )
    }

    @Test
    fun `idle prehold refreshes before expiry while a launch-ready packet route owns recovery`() {
        assertFalse(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = false,
            routeCanRecoverCharge = true,
            ticksUsingItem = 18,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
        assertTrue(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = false,
            routeCanRecoverCharge = true,
            ticksUsingItem = 19,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
        assertFalse(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = true,
            routeCanRecoverCharge = true,
            ticksUsingItem = 20,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
        assertTrue(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = true,
            routeCanRecoverCharge = false,
            ticksUsingItem = 20,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
    }

    @Test
    fun `prehold refresh ignores inactive and invalid spear use`() {
        assertFalse(shouldRefreshSpearKillPrehold(
            useRequested = false,
            launchCandidateReady = false,
            routeCanRecoverCharge = true,
            ticksUsingItem = 19,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
        assertFalse(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = false,
            routeCanRecoverCharge = true,
            ticksUsingItem = -1,
            delayTicks = 3,
            damageUseDuration = 20,
        ))
        assertFalse(shouldRefreshSpearKillPrehold(
            useRequested = true,
            launchCandidateReady = false,
            routeCanRecoverCharge = true,
            ticksUsingItem = 19,
            delayTicks = -1,
            damageUseDuration = 20,
        ))
    }

    @Test
    fun `only a transient weapon state keeps route preparation active`() {
        assertTrue(SpearKillAttackStartResult.RETRY_LATER.keepsRoutePreparation)
        assertFalse(SpearKillAttackStartResult.STARTED.keepsRoutePreparation)
        assertFalse(SpearKillAttackStartResult.BLOCKED.keepsRoutePreparation)
        assertTrue(shouldRestartSpearKillCharge(SpearKillAttackStartResult.RETRY_LATER))
        assertFalse(shouldRestartSpearKillCharge(SpearKillAttackStartResult.STARTED))
    }
}
