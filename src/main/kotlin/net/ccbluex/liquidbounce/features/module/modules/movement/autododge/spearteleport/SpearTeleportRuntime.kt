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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/** Owns spear-teleport planning, cooldown, execution, and compact debug state. */
internal class SpearTeleportRuntime(
    private val planner: SpearTeleportPlanner = SpearTeleportPlanner(),
    private val cooldown: SpearTeleportCooldown = SpearTeleportCooldown(),
    private val chooseLateralSide: () -> SpearTeleportLateralSide = {
        if (Random.nextBoolean()) SpearTeleportLateralSide.POSITIVE else SpearTeleportLateralSide.NEGATIVE
    },
) {
    var plannedTeleport: SpearTeleportPlan? = null
        private set

    var state = SpearTeleportState.IDLE
        private set

    fun plan(
        enabled: Boolean,
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        tick: Long,
        playerPosition: Vec3,
        threat: CombatTeleportThreat?,
        settings: SpearTeleportSettings,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? = planThreat(
        enabled,
        canStartDefense,
        projectilePlanActive,
        tick,
        playerPosition,
        threat,
        settings,
        isSafe,
    )

    fun planMace(
        enabled: Boolean,
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        tick: Long,
        playerPosition: Vec3,
        threat: CombatTeleportThreat?,
        settings: SpearTeleportSettings,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? = planThreat(
        enabled,
        canStartDefense,
        projectilePlanActive,
        tick,
        playerPosition,
        threat,
        settings,
        isSafe,
    )

    private fun planThreat(
        enabled: Boolean,
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        tick: Long,
        playerPosition: Vec3,
        threat: CombatTeleportThreat?,
        settings: SpearTeleportSettings,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? {
        plannedTeleport = null
        state = resolveState(enabled, canStartDefense, projectilePlanActive, threat, tick, settings)
        if (state != SpearTeleportState.PLANNING || threat == null) {
            return null
        }

        val request = SpearTeleportRequest(
            playerPosition = playerPosition.toSpearTeleportPoint(),
            attackerPosition = threat.position.toSpearTeleportPoint(),
            attackerLook = SpearTeleportDirection(threat.lookDirection.x, threat.lookDirection.z),
            behindDistance = settings.behindDistance,
            lateralDistance = settings.behindDistance
                .coerceAtLeast(MINIMUM_LATERAL_DISTANCE)
                .coerceAtMost(settings.maxDistance),
            maxDistance = settings.maxDistance,
            searchRadius = settings.searchRadius,
            preferredLateralSide = chooseLateralSide(),
            preferLocalEscape = !threat.trustsAttackerLook,
        )
        plannedTeleport = planner.plan(request, isSafe)
        state = if (plannedTeleport == null) {
            SpearTeleportState.NO_SAFE_DESTINATION
        } else {
            SpearTeleportState.READY
        }
        return plannedTeleport
    }

    fun execute(
        tick: Long,
        from: Vec3,
        plan: SpearTeleportPlan,
        settings: SpearTeleportSettings,
        onGround: Boolean,
        horizontalCollision: Boolean,
        isStillSafe: () -> Boolean,
        sendPacket: (ServerboundMovePlayerPacket) -> Unit,
        moveLocalPlayer: (Vec3) -> Unit,
    ): Boolean {
        if (!isStillSafe()) {
            plannedTeleport = null
            state = SpearTeleportState.SAFETY_RECHECK_REJECTED
            return false
        }
        val executed = executeSpearTeleport(
            from,
            plan,
            settings.stepDistance,
            settings.maxPackets,
            onGround,
            horizontalCollision,
            isStillSafe = { true },
            sendPacket = sendPacket,
            moveLocalPlayer = moveLocalPlayer,
        )
        if (!executed) {
            plannedTeleport = null
            state = SpearTeleportState.PACKET_BUDGET_REJECTED
            return false
        }

        cooldown.recordSuccess(tick)
        plannedTeleport = plan
        state = SpearTeleportState.TELEPORTED
        return true
    }

    fun reset() {
        cooldown.reset()
        plannedTeleport = null
        state = SpearTeleportState.IDLE
    }

    private fun resolveState(
        enabled: Boolean,
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        threat: CombatTeleportThreat?,
        tick: Long,
        settings: SpearTeleportSettings,
    ) = when {
        !enabled -> SpearTeleportState.DISABLED
        projectilePlanActive -> SpearTeleportState.PROJECTILE_PRIORITY
        !canStartDefense || threat == null -> SpearTeleportState.NO_THREAT
        !cooldown.isReady(tick, settings.cooldownTicks) -> SpearTeleportState.COOLDOWN
        else -> SpearTeleportState.PLANNING
    }
}

private fun Vec3.toSpearTeleportPoint() = SpearTeleportPoint(x, y, z)

private const val MINIMUM_LATERAL_DISTANCE = 1.5
