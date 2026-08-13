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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SpearTeleportPlannerTest {

    @Test
    fun `selects the safe point directly behind the attacker`() {
        val plan = planner.plan(request()) { true }

        assertEquals(SpearTeleportPoint(-2.0, 64.0, 0.0), plan?.destination)
        assertEquals(7.0, plan?.travelDistance)
    }

    @Test
    fun `falls back to the nearest safe point behind the attacker`() {
        val expected = SpearTeleportPoint(-2.0, 64.0, 1.0)

        val plan = planner.plan(request()) { candidate -> candidate == expected }

        assertEquals(expected, plan?.destination)
    }

    @Test
    fun `uses the attacker to player axis when remote look is unavailable`() {
        val plan = planner.plan(request(attackerLookX = 0.0, attackerLookZ = 0.0)) { true }

        assertEquals(SpearTeleportPoint(-2.0, 64.0, 0.0), plan?.destination)
    }

    @Test
    fun `refuses a teleport beyond the configured maximum distance`() {
        val plan = planner.plan(
            request(maxDistance = 6.0, searchRadius = 0, lateralDistance = 8.0),
        ) { true }

        assertNull(plan)
    }

    @Test
    fun `distant attacker falls back to a short lateral teleport`() {
        val plan = planner.plan(
            request(playerX = 50.0, maxDistance = 12.0, searchRadius = 0, lateralDistance = 3.0),
        ) { true }

        assertEquals(SpearTeleportPoint(50.0, 64.0, 3.0), plan?.destination)
        assertEquals(3.0, plan?.travelDistance)
    }

    @Test
    fun `returns no plan when every behind point is unsafe`() {
        val plan = planner.plan(request()) { false }

        assertNull(plan)
    }

    @Test
    fun `bounded path keeps every packet step within the configured distance`() {
        val path = buildSpearTeleportPath(
            from = Vec3.ZERO,
            to = Vec3(9.0, 0.0, 0.0),
            stepDistance = 4.0,
            maxPackets = 3,
        )

        assertEquals(3, path?.size)
        assertEquals(Vec3(9.0, 0.0, 0.0), path?.last())
        assertTrue(path.orEmpty().zipWithNext(Vec3.ZERO).all { (from, to) -> from.distanceTo(to) <= 4.0 })
    }

    @Test
    fun `bounded path refuses to inflate steps past the packet budget`() {
        val path = buildSpearTeleportPath(
            from = Vec3.ZERO,
            to = Vec3(9.0, 0.0, 0.0),
            stepDistance = 4.0,
            maxPackets = 2,
        )

        assertNull(path)
    }

    @Test
    fun `teleport packet changes position without changing rotation`() {
        val packet = createSpearTeleportPacket(
            position = Vec3(4.0, 65.0, -3.0),
            onGround = true,
            horizontalCollision = false,
        )

        assertTrue(packet is ServerboundMovePlayerPacket.Pos)
        assertTrue(packet.hasPos)
        assertFalse(packet.hasRot)
        assertEquals(4.0, packet.x)
        assertEquals(65.0, packet.y)
        assertEquals(-3.0, packet.z)
        assertTrue(packet.isOnGround)
        assertFalse(packet.horizontalCollision())
    }

    @Test
    fun `teleport execution sends its bounded path before moving the local player`() {
        val events = mutableListOf<String>()
        val sentPositions = mutableListOf<Vec3>()
        val plan = SpearTeleportPlan(SpearTeleportPoint(8.0, 64.0, 0.0), travelDistance = 8.0)

        val executed = executeSpearTeleport(
            from = Vec3(0.0, 64.0, 0.0),
            plan = plan,
            stepDistance = 4.0,
            maxPackets = 2,
            onGround = true,
            horizontalCollision = false,
            isStillSafe = { true },
            sendPacket = { packet ->
                events += "packet"
                sentPositions += Vec3(packet.x, packet.y, packet.z)
                assertFalse(packet.hasRot)
            },
            moveLocalPlayer = {
                events += "move"
                assertEquals(plan.destination.toVec3(), it)
            },
        )

        assertTrue(executed)
        assertEquals(listOf("packet", "packet", "move"), events)
        assertEquals(listOf(Vec3(4.0, 64.0, 0.0), Vec3(8.0, 64.0, 0.0)), sentPositions)
    }

    @Test
    fun `teleport execution leaves local state untouched when packet budget is insufficient`() {
        var sent = false
        var moved = false
        val plan = SpearTeleportPlan(SpearTeleportPoint(9.0, 64.0, 0.0), travelDistance = 9.0)

        val executed = executeSpearTeleport(
            from = Vec3(0.0, 64.0, 0.0),
            plan = plan,
            stepDistance = 4.0,
            maxPackets = 2,
            onGround = true,
            horizontalCollision = false,
            isStillSafe = { true },
            sendPacket = { sent = true },
            moveLocalPlayer = { moved = true },
        )

        assertFalse(executed)
        assertFalse(sent)
        assertFalse(moved)
    }

    @Test
    fun `teleport execution rechecks block safety before sending any packet`() {
        var sent = false
        var moved = false
        val plan = SpearTeleportPlan(SpearTeleportPoint(4.0, 64.0, 0.0), travelDistance = 4.0)

        val executed = executeSpearTeleport(
            from = Vec3(0.0, 64.0, 0.0),
            plan = plan,
            stepDistance = 4.0,
            maxPackets = 1,
            onGround = true,
            horizontalCollision = false,
            isStillSafe = { false },
            sendPacket = { sent = true },
            moveLocalPlayer = { moved = true },
        )

        assertFalse(executed)
        assertFalse(sent)
        assertFalse(moved)
    }

    @Test
    fun `teleport landing requires collision clearance support and non-void ground`() {
        assertTrue(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = true,
                overVoid = false,
                routeCollisionFree = true,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = false,
                supported = true,
                overVoid = false,
                routeCollisionFree = true,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = false,
                overVoid = false,
                routeCollisionFree = true,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = true,
                overVoid = true,
                routeCollisionFree = true,
            )
        )
        assertFalse(
            isSpearTeleportCandidateSafe(
                destinationCollisionFree = true,
                supported = true,
                overVoid = false,
                routeCollisionFree = false,
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
        val runtime = SpearTeleportRuntime()

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

    private fun request(
        playerX: Double = 5.0,
        attackerLookX: Double = 1.0,
        attackerLookZ: Double = 0.0,
        maxDistance: Double = 12.0,
        searchRadius: Int = 2,
        lateralDistance: Double = 2.0,
    ) = SpearTeleportRequest(
        playerPosition = SpearTeleportPoint(playerX, 64.0, 0.0),
        attackerPosition = SpearTeleportPoint(0.0, 64.0, 0.0),
        attackerLook = SpearTeleportDirection(attackerLookX, attackerLookZ),
        behindDistance = 2.0,
        lateralDistance = lateralDistance,
        maxDistance = maxDistance,
        searchRadius = searchRadius,
    )

    private fun threat() = SpearThreat(
        candidate = SpearThreatCandidate(
            entityId = 7,
            name = "attacker",
            position = Vec3.ZERO,
            eyePosition = Vec3(0.0, 1.62, 0.0),
            lookDirection = Vec3(1.0, 0.0, 0.0),
            isHoldingSpear = true,
            isUsingSpear = true,
        ),
        kind = SpearThreatKind.USING_AIMED,
        distanceSquared = 25.0,
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
