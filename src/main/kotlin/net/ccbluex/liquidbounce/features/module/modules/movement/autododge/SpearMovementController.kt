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

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class SpearMovementSettings(
    val enabled: Boolean,
    val aimMargin: Double,
    val visibilityGraceTicks: Int,
    val jukeTicks: IntRange,
    val threatMemoryTicks: Int,
    val teleportEnabled: Boolean,
    val teleport: SpearTeleportSettings,
)

internal data class SpearMovementResult(
    val threat: SpearThreat?,
    val jukePlan: SpearDodgePlan?,
    val teleportPlan: SpearTeleportPlan?,
)

/** Coordinates predictive spear threat selection and movement responses, leaving shields independent. */
internal class SpearMovementController(
    private val threatDetector: SpearThreatDetector = SpearThreatDetector(),
    private val dodgePlanner: SpearDodgePlanner = SpearDodgePlanner(),
    private val jukeCommitment: SpearJukeCommitment = SpearJukeCommitment(),
    private val teleportRuntime: SpearTeleportRuntime = SpearTeleportRuntime(),
) {
    var primaryThreat: SpearThreat? = null
        private set

    var jukeDecision: SpearJukeDecision? = null
        private set

    private var committedThreatId: Int? = null
    private var committedResponse: SpearThreatResponse? = null

    val teleportState: SpearTeleportState
        get() = teleportRuntime.state

    val plannedTeleport: SpearTeleportPlan?
        get() = teleportRuntime.plannedTeleport

    fun update(
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        player: LocalPlayer,
        world: ClientLevel,
        settings: SpearMovementSettings,
    ): SpearMovementResult {
        val threat = updateThreatOnly(canStartDefense, player, world, settings)
        val jukePlan = updateJuke(canStartDefense, threat, player, world, settings)
        val teleportPlan = teleportRuntime.plan(
            enabled = settings.enabled && settings.teleportEnabled,
            canStartDefense = canStartDefense && !projectilePlanActive,
            projectilePlanActive = projectilePlanActive,
            tick = player.tickCount.toLong(),
            playerPosition = player.position(),
            threat = threat.takeIf { it.requiresTeleport },
            settings = settings.teleport,
            isSafe = { candidate ->
                isSafeSpearTeleportCandidate(world, player, settings.teleport, candidate)
            },
        )
        return SpearMovementResult(threat, jukePlan, teleportPlan)
    }

    fun executeTeleport(
        player: LocalPlayer,
        world: ClientLevel,
        plan: SpearTeleportPlan,
        settings: SpearTeleportSettings,
        sendPacket: (ServerboundMovePlayerPacket) -> Unit,
    ): Boolean {
        val origin = player.position()
        val executed = teleportRuntime.execute(
            tick = player.tickCount.toLong(),
            from = origin,
            plan = plan,
            settings = settings,
            onGround = player.onGround() && plan.destination.y == origin.y,
            horizontalCollision = player.horizontalCollision,
            isStillSafe = {
                isSafeSpearTeleportCandidate(world, player, settings, plan.destination)
            },
            sendPacket = sendPacket,
            moveLocalPlayer = { destination ->
                player.setPos(destination)
                player.deltaMovement = Vec3.ZERO
            },
        )
        if (executed) {
            resetCommitment()
        }
        return executed
    }

    fun resetMovement() {
        threatDetector.reset()
        primaryThreat = null
        resetCommitment()
    }

    fun resetTeleport() {
        teleportRuntime.reset()
    }

    /** Updates the selected spear threat without planning or executing a movement response. */
    fun updateThreatOnly(
        canStartDefense: Boolean,
        player: LocalPlayer,
        world: ClientLevel,
        settings: SpearMovementSettings,
    ): SpearThreat? {
        if (!settings.enabled || !canStartDefense) {
            resetMovement()
            return null
        }

        primaryThreat = threatDetector.update(
            target = SpearThreatTargetSnapshot(player.boundingBox, player.deltaMovement),
            candidates = world.players().asSequence()
                .filterIsInstance<RemotePlayer>()
                .map { it.toSpearThreatCandidate() }
                .asIterable(),
            aimMargin = settings.aimMargin,
            threatMemoryTicks = settings.threatMemoryTicks,
            visibilityGraceTicks = settings.visibilityGraceTicks,
        )
        if (primaryThreat == null) {
            resetCommitment()
        }
        return primaryThreat
    }

    private fun updateJuke(
        canStartDefense: Boolean,
        threat: SpearThreat?,
        player: LocalPlayer,
        world: ClientLevel,
        settings: SpearMovementSettings,
    ): SpearDodgePlan? {
        if (!settings.enabled || !canStartDefense || !threat.requiresJuke) {
            resetCommitment()
            return null
        }
        checkNotNull(threat)

        if (committedThreatId != threat.candidate.entityId || committedResponse != threat.response) {
            jukeCommitment.reset()
            jukeDecision = null
            committedThreatId = threat.candidate.entityId
            committedResponse = threat.response
        }

        val playerPosition = player.position().horizontalPosition()
        val startedSafelyGrounded = player.onGround() && isSupported(world, player, player.boundingBox) &&
            !player.wouldFallIntoVoid(player.position(), world.minY.toDouble())
        jukeDecision = jukeCommitment.update(
            durationTicks = if (threat.response == SpearThreatResponse.FEINT) 1..1 else settings.jukeTicks,
            isCurrentInputSafe = { input ->
                dodgePlanner.isSafeSimulation(
                    simulation = simulateMovement(input, player, world),
                    playerPosition = playerPosition,
                    startedSafelyGrounded = startedSafelyGrounded,
                )
            },
            replan = {
                dodgePlanner.plan(
                    attackerPosition = threat.candidate.position.horizontalPosition(),
                    playerPosition = playerPosition,
                    attackDirection = if (threat.trustsAttackerLook) {
                        SpearTeleportDirection(
                            threat.candidate.lookDirection.x,
                            threat.candidate.lookDirection.z,
                        )
                    } else {
                        PACKET_ESCAPE_AXIS
                    },
                    startedSafelyGrounded = startedSafelyGrounded,
                    safeDistance = DodgePlanner.SAFE_DISTANCE_WITH_PADDING,
                    simulate = { simulateMovement(it, player, world) },
                )
            },
        )
        return jukeDecision?.plan
    }

    private fun simulateMovement(
        input: DirectionalInput,
        player: LocalPlayer,
        world: ClientLevel,
    ): SpearMovementSimulation {
        val simulatedInput = SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(
            directionalInput = input,
            jump = false,
            sprinting = player.isSprinting,
            sneaking = player.isShiftKeyDown,
        )
        val simulatedPlayer = SimulatedPlayer.fromClientPlayer(simulatedInput)
        return collectSpearMovementSimulation(
            tick = simulatedPlayer::tick,
            sample = {
                SpearMovementSample(
                    position = simulatedPlayer.pos.horizontalPosition(),
                    colliding = simulatedPlayer.horizontalCollision,
                    supported = simulatedPlayer.onGround || isSupported(world, player, simulatedPlayer.boundingBox),
                    overVoid = player.wouldFallIntoVoid(simulatedPlayer.pos, world.minY.toDouble()),
                )
            },
        )
    }

    private fun RemotePlayer.toSpearThreatCandidate(): SpearThreatCandidate {
        val currentPosition = position()
        val previousPosition = Vec3(xOld, yOld, zOld)
        val usingSpear = isUsingItem && useItem.isSpear
        val kineticWeapon = useItem.get(DataComponents.KINETIC_WEAPON).takeIf { usingSpear }
        return SpearThreatCandidate(
            entityId = id,
            name = scoreboardName,
            position = currentPosition,
            eyePosition = eyePosition,
            lookDirection = lookAngle,
            isHoldingSpear = mainHandItem.isSpear || offhandItem.isSpear,
            isUsingSpear = usingSpear,
            spearUseTicks = ticksUsingItem.takeIf { usingSpear } ?: 0,
            spearDelayTicks = kineticWeapon?.delayTicks,
            spearDamageUseDurationTicks = kineticWeapon?.computeDamageUseDuration(),
            isAlive = isAlive,
            isRemoved = isRemoved,
            isBot = ModuleAntiBot.isBot(this),
            hasSignificantPositionJump = currentPosition.distanceToSqr(previousPosition) >=
                SIGNIFICANT_POSITION_JUMP_SQ,
            visibilityAgeTicks = tickCount.coerceAtLeast(0),
        )
    }

    private fun resetCommitment() {
        jukeCommitment.reset()
        committedThreatId = null
        committedResponse = null
        jukeDecision = null
    }

    private companion object {
        const val SIGNIFICANT_POSITION_JUMP_SQ = 4.0
        const val SUPPORT_CHECK_DEPTH = 0.05
        val PACKET_ESCAPE_AXIS = SpearTeleportDirection(1.0, 0.0)

        fun Vec3.horizontalPosition() = HorizontalPosition(x, z)

        fun isSupported(world: ClientLevel, player: LocalPlayer, boundingBox: AABB): Boolean =
            world.getBlockCollisions(
                player,
                boundingBox.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0),
            ).anyNotEmpty()
    }
}
