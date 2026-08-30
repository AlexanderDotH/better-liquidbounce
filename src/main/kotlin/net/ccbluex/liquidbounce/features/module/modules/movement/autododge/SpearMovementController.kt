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

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.CombatTeleportThreat
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportPlan
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportRuntime
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportSettings
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportState
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.isSafeSpearTeleportCandidate

import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

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
            threat = threat.takeIf { it.requiresTeleport }?.let {
                CombatTeleportThreat(it.candidate.position, it.candidate.lookDirection, it.trustsAttackerLook)
            },
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

        prepareJukeCommitment(threat)
        val playerPosition = player.position().horizontalPosition()
        val startedSafelyGrounded = player.onGround() && isSpearMovementSupported(world, player, player.boundingBox) &&
            !player.wouldFallIntoVoid(player.position(), world.minY.toDouble())
        jukeDecision = buildJukeDecision(threat, player, world, settings, playerPosition, startedSafelyGrounded)
        return jukeDecision?.plan
    }

    private fun prepareJukeCommitment(threat: SpearThreat) {
        if (committedThreatId == threat.candidate.entityId && committedResponse == threat.response) return
        jukeCommitment.reset()
        jukeDecision = null
        committedThreatId = threat.candidate.entityId
        committedResponse = threat.response
    }

    private fun buildJukeDecision(
        threat: SpearThreat,
        player: LocalPlayer,
        world: ClientLevel,
        settings: SpearMovementSettings,
        playerPosition: HorizontalPosition,
        startedSafelyGrounded: Boolean,
    ) = jukeCommitment.update(
            durationTicks = if (threat.response == SpearThreatResponse.FEINT) 1..1 else settings.jukeTicks,
            isCurrentInputSafe = { input ->
                dodgePlanner.isSafeSimulation(
                    simulation = simulateSpearMovement(input, player, world),
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
                    simulate = { simulateSpearMovement(it, player, world) },
                )
            },
        )

    private fun resetCommitment() {
        jukeCommitment.reset()
        committedThreatId = null
        committedResponse = null
        jukeDecision = null
    }

}

private val PACKET_ESCAPE_AXIS = SpearTeleportDirection(1.0, 0.0)
