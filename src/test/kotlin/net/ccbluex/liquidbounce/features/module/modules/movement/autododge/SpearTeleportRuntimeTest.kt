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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportCooldown
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.CombatTeleportThreat
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportLateralSide
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportPlanner
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportPoint
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportRequest
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportRuntime
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportSettings
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportState
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.buildSpearTeleportCollisionSamples
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.isSpearTeleportCandidateSafe

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SpearTeleportRuntimeTest {
    @Test
    fun `airborne lateral escape keeps collision checks without requiring a landing block`() {
        assertTrue(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = false,
                overVoid = true,
                routeCollisionFree = true,
                requiresLandingSupport = false,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = false,
                supported = false,
                overVoid = true,
                routeCollisionFree = true,
                requiresLandingSupport = false,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = false,
                overVoid = true,
                routeCollisionFree = false,
                requiresLandingSupport = false,
            )
        )
    }

    @Test
    fun `collision sampling checks space between packet endpoints`() {
        val samples = buildSpearTeleportCollisionSamples(
            from = Vec3.ZERO,
            to = Vec3(4.0, 0.0, 0.0),
        )

        assertTrue(samples.any { it.x in 1.9..2.1 })
        assertEquals(Vec3(4.0, 0.0, 0.0), samples.last())
        assertTrue(samples.zipWithNext(Vec3.ZERO).all { (from, to) ->
            from.distanceTo(to) <= SPEAR_TELEPORT_COLLISION_SAMPLE_DISTANCE
        })
    }

    @Test
    fun `cooldown becomes ready exactly on its configured tick`() {
        val cooldown = SpearTeleportCooldown()

        assertTrue(cooldown.isReady(tick = 10, cooldownTicks = 6))
        cooldown.recordSuccess(tick = 10)

        assertFalse(cooldown.isReady(tick = 15, cooldownTicks = 6))
        assertTrue(cooldown.isReady(tick = 16, cooldownTicks = 6))
    }

    @Test
    fun `world reset clears the teleport cooldown`() {
        val cooldown = SpearTeleportCooldown()
        cooldown.recordSuccess(tick = 10)

        cooldown.reset()

        assertTrue(cooldown.isReady(tick = 11, cooldownTicks = 40))
    }

    @Test
    fun `runtime cooldown begins only after a successful teleport`() {
        val runtime = SpearTeleportRuntime()
        val settings = teleportSettings(cooldownTicks = 6)
        val initial = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 10,
            playerPosition = Vec3(5.0, 64.0, 0.0),
            threat = threat(),
            settings = settings,
            isSafe = { true },
        )

        assertEquals(SpearTeleportState.READY, runtime.state)
        assertTrue(runtime.execute(10, Vec3(5.0, 64.0, 0.0), initial!!, settings, true, false, { true }, {}, {}))
        assertEquals(SpearTeleportState.TELEPORTED, runtime.state)

        val coolingDown = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 15,
            playerPosition = Vec3(5.0, 64.0, 0.0),
            threat = threat(),
            settings = settings,
            isSafe = { true },
        )
        assertNull(coolingDown)
        assertEquals(SpearTeleportState.COOLDOWN, runtime.state)

        val readyAgain = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 16,
            playerPosition = Vec3(5.0, 64.0, 0.0),
            threat = threat(),
            settings = settings,
            isSafe = { true },
        )
        assertEquals(SpearTeleportState.READY, runtime.state)
        assertEquals(SpearTeleportPoint(-2.0, 64.0, 0.0), readyAgain?.destination)
    }

    @Test
    fun `runtime never plans a teleport while projectile movement has priority`() {
        val runtime = SpearTeleportRuntime()

        val plan = runtime.plan(
            enabled = true,
            canStartDefense = false,
            projectilePlanActive = true,
            tick = 10,
            playerPosition = Vec3(5.0, 64.0, 0.0),
            threat = threat(),
            settings = teleportSettings(),
            isSafe = { true },
        )

        assertNull(plan)
        assertEquals(SpearTeleportState.PROJECTILE_PRIORITY, runtime.state)
    }

    @Test
    fun `runtime keeps lateral fallback outside the spear danger line at minimum settings`() {
        val runtime = SpearTeleportRuntime(
            chooseLateralSide = { SpearTeleportLateralSide.POSITIVE },
        )

        val plan = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 10,
            playerPosition = Vec3(50.0, 64.0, 0.0),
            threat = threat(),
            settings = teleportSettings(behindDistance = 0.5, searchRadius = 0),
            isSafe = { true },
        )

        assertEquals(SpearTeleportPoint(50.0, 64.0, DodgePlanner.SAFE_DISTANCE_WITH_PADDING), plan?.destination)
    }

    @Test
    fun `runtime applies its per burst lateral side choice`() {
        val runtime = SpearTeleportRuntime(
            chooseLateralSide = { SpearTeleportLateralSide.NEGATIVE },
        )

        val plan = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 10,
            playerPosition = Vec3(50.0, 64.0, 0.0),
            threat = threat(),
            settings = teleportSettings(behindDistance = 3.0, searchRadius = 0),
            isSafe = { true },
        )

        assertEquals(SpearTeleportPoint(50.0, 64.0, -3.0), plan?.destination)
    }

    @Test
    fun `runtime uses local escape for packet capable threat instead of remote look`() {
        val runtime = SpearTeleportRuntime(
            chooseLateralSide = { SpearTeleportLateralSide.POSITIVE },
        )
        val packetThreat = threat().copy(
            position = Vec3(0.0, 320.0, 0.0),
            lookDirection = Vec3.ZERO,
            trustsAttackerLook = false,
        )

        val plan = runtime.plan(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 10,
            playerPosition = Vec3(0.0, 64.0, 0.0),
            threat = packetThreat,
            settings = teleportSettings(behindDistance = 3.0, searchRadius = 0),
            isSafe = { true },
        )

        assertEquals(SpearTeleportPoint(0.0, 64.0, 3.0), plan?.destination)
    }

    @Test
    fun `runtime gives a distant packet capable mace holder a local escape without line of sight`() {
        val runtime = SpearTeleportRuntime(
            chooseLateralSide = { SpearTeleportLateralSide.POSITIVE },
        )
        val maceThreat = CombatTeleportThreat(
            position = Vec3(-0.5, 0.0001, -0.5),
            lookDirection = Vec3.ZERO,
            trustsAttackerLook = false,
        )

        val plan = runtime.planMace(
            enabled = true,
            canStartDefense = true,
            projectilePlanActive = false,
            tick = 10,
            playerPosition = Vec3(-14.5, 92.0, 26.7),
            threat = maceThreat,
            settings = teleportSettings(behindDistance = 3.0, searchRadius = 0),
            isSafe = { true },
        )

        assertEquals(SpearTeleportPoint(-14.5, 92.0, 29.7), plan?.destination)
    }

    private fun request(
        playerX: Double = 5.0,
        attackerLookX: Double = 1.0,
        attackerLookZ: Double = 0.0,
        maxDistance: Double = 12.0,
        searchRadius: Int = 2,
        lateralDistance: Double = 2.0,
        preferredLateralSide: SpearTeleportLateralSide = SpearTeleportLateralSide.POSITIVE,
        preferLocalEscape: Boolean = false,
    ) = SpearTeleportRequest(
        playerPosition = SpearTeleportPoint(playerX, 64.0, 0.0),
        attackerPosition = SpearTeleportPoint(0.0, 64.0, 0.0),
        attackerLook = SpearTeleportDirection(attackerLookX, attackerLookZ),
        behindDistance = 2.0,
        lateralDistance = lateralDistance,
        maxDistance = maxDistance,
        searchRadius = searchRadius,
        preferredLateralSide = preferredLateralSide,
        preferLocalEscape = preferLocalEscape,
    )

    private fun threat() = CombatTeleportThreat(
        position = Vec3.ZERO,
        lookDirection = Vec3(1.0, 0.0, 0.0),
        trustsAttackerLook = true,
    )

    private fun teleportSettings(
        cooldownTicks: Int = 6,
        behindDistance: Double = 2.0,
        searchRadius: Int = 2,
    ) = SpearTeleportSettings(
        behindDistance = behindDistance,
        maxDistance = 12.0,
        searchRadius = searchRadius,
        cooldownTicks = cooldownTicks,
        stepDistance = 4.0,
        maxPackets = 8,
    )

    private companion object {
        val planner = SpearTeleportPlanner()
    }
}

private fun List<Vec3>.zipWithNext(origin: Vec3): List<Pair<Vec3, Vec3>> {
    val withOrigin = listOf(origin) + this
    return withOrigin.zipWithNext()
}
