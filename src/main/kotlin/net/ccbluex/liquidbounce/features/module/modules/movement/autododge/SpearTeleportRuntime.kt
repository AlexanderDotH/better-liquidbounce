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

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.minecraft.core.BlockPos
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

internal data class SpearTeleportSettings(
    val behindDistance: Double,
    val maxDistance: Double,
    val searchRadius: Int,
    val cooldownTicks: Int,
    val stepDistance: Double,
    val maxPackets: Int,
)

internal class SpearTeleportValueGroup(
    parent: EventListener,
    private val resetRuntime: () -> Unit,
    defaultEnabled: Boolean = false,
) : ToggleableValueGroup(parent, "Teleport", defaultEnabled) {
    val behindDistance by float("BehindDistance", 2.0F, 0.5F..5.0F, suffix = "blocks")
    val maxDistance by float("MaxDistance", 12.0F, 2.0F..32.0F, suffix = "blocks")
    val searchRadius by int("SearchRadius", 2, 0..5, suffix = "blocks")
    val cooldown by int("Cooldown", 6, 0..40, suffix = "ticks")
    val stepDistance by float("StepDistance", 4.0F, 0.25F..10.0F, suffix = "blocks")
    val maxPackets by int("MaxPackets", 8, 1..32)

    fun settings() = SpearTeleportSettings(
        behindDistance = behindDistance.toDouble(),
        maxDistance = maxDistance.toDouble(),
        searchRadius = searchRadius,
        cooldownTicks = cooldown,
        stepDistance = stepDistance.toDouble(),
        maxPackets = maxPackets,
    )

    override fun onDisabled() {
        resetRuntime()
        super.onDisabled()
    }
}

internal enum class SpearTeleportState(val debugName: String) {
    IDLE("Idle"),
    DISABLED("Disabled"),
    PROJECTILE_PRIORITY("ProjectilePriority"),
    NO_THREAT("NoThreat"),
    COOLDOWN("Cooldown"),
    PLANNING("Planning"),
    NO_SAFE_DESTINATION("NoSafeDestination"),
    READY("Ready"),
    SAFETY_RECHECK_REJECTED("SafetyRecheckRejected"),
    PACKET_BUDGET_REJECTED("PacketBudgetRejected"),
    TELEPORTED("Teleported"),
}

private data class CombatTeleportThreat(
    val position: Vec3,
    val lookDirection: Vec3,
    val trustsAttackerLook: Boolean,
)

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
        threat: SpearThreat?,
        settings: SpearTeleportSettings,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? = planThreat(
        enabled,
        canStartDefense,
        projectilePlanActive,
        tick,
        playerPosition,
        threat?.let {
            CombatTeleportThreat(it.candidate.position, it.candidate.lookDirection, it.trustsAttackerLook)
        },
        settings,
        isSafe,
    )

    fun planMace(
        enabled: Boolean,
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        tick: Long,
        playerPosition: Vec3,
        threat: MaceThreat?,
        settings: SpearTeleportSettings,
        isSafe: (SpearTeleportPoint) -> Boolean,
    ): SpearTeleportPlan? = planThreat(
        enabled,
        canStartDefense,
        projectilePlanActive,
        tick,
        playerPosition,
        threat?.let {
            CombatTeleportThreat(it.candidate.position, it.candidate.lookDirection, trustsAttackerLook = false)
        },
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
                .coerceAtLeast(DodgePlanner.SAFE_DISTANCE_WITH_PADDING)
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

internal fun isSafeSpearTeleportCandidate(
    world: ClientLevel,
    player: LocalPlayer,
    settings: SpearTeleportSettings,
    candidate: SpearTeleportPoint,
): Boolean {
    val destination = candidate.toVec3()
    val dimensions = player.getDimensions(Pose.STANDING)
    val destinationBox = dimensions.makeBoundingBox(destination)
    val requiresLandingSupport = player.onGround()
    val supported = !requiresLandingSupport || world.getBlockCollisions(
        player,
        destinationBox.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0),
    ).anyNotEmpty()
    val overVoid = requiresLandingSupport && player.wouldFallIntoVoid(destination, world.minY.toDouble())
    val landingSafe = isSpearTeleportCandidateSafe(
        destinationCollisionFree = world.noCollision(player, destinationBox),
        supported = supported,
        overVoid = overVoid,
        routeCollisionFree = true,
        loaded = world.hasChunkAt(BlockPos.containing(destination)),
        withinWorldBorder = world.worldBorder.isWithinBounds(destinationBox),
        requiresLandingSupport = requiresLandingSupport,
    )
    if (!landingSafe) {
        return false
    }

    val path = buildSpearTeleportPath(
        player.position(),
        destination,
        settings.stepDistance,
        settings.maxPackets,
    ) ?: return false
    val routeCollisionFree = buildSpearTeleportCollisionSamples(player.position(), destination).all { point ->
        world.getBlockCollisions(player, dimensions.makeBoundingBox(point)).allEmpty()
    }
    val routeSafe = isSpearTeleportCandidateSafe(
        destinationCollisionFree = true,
        supported = true,
        overVoid = false,
        routeCollisionFree = routeCollisionFree,
    )
    if (!routeSafe) {
        return false
    }

    // Keep packet endpoints covered explicitly even though the denser route sweep includes them.
    return path.all { point ->
        world.getBlockCollisions(player, dimensions.makeBoundingBox(point)).allEmpty()
    }
}

private fun Vec3.toSpearTeleportPoint() = SpearTeleportPoint(x, y, z)

private const val SUPPORT_CHECK_DEPTH = 0.05
