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

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportPlan

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

internal class AutoDodgeDefenseRuntime : MinecraftShortcuts {

    private val spearMovementController = SpearMovementController()
    private val maceMovementController = MaceMovementController()
    private val packetController = AutoDodgePacketController()
    private val projectileRuntime = AutoDodgeProjectileRuntime()

    var primarySpearThreat: SpearThreat? = null
        private set
    var primaryMaceThreat: MaceThreat? = null
        private set

    val spearJukeDecision get() = spearMovementController.jukeDecision
    val spearTeleportState get() = spearMovementController.teleportState
    val spearTeleportPlan get() = spearMovementController.plannedTeleport
    val maceTeleportState get() = maceMovementController.teleportState
    val maceTeleportPlan get() = maceMovementController.plannedTeleport
    val packetDebug get() = packetController.debug

    fun handleInput(
        event: MovementInputEvent,
        moduleEnabled: Boolean,
        packetMode: Boolean,
        context: AutoDodgeRuntimeContext,
    ): Boolean {
        val availability = resolveAutoDodgeBranchAvailability(context)
        val canStartDefense = moduleEnabled && availability.spear
        val projectileHit = if (moduleEnabled && availability.projectile) {
            projectileRuntime.findImmediateThreat()
        } else {
            null
        }
        if (packetMode) {
            updatePacketDefense(canStartDefense, projectileHit)
        } else {
            updateMovementDefense(event, canStartDefense, projectileHit)
        }
        return canStartDefense
    }

    fun shouldSuppressPacket(event: PacketEvent) =
        shouldSuppressAutoDodgePacketHoldMovement(packetController.suppressesMovementPackets, event)

    private fun updateMovementDefense(
        event: MovementInputEvent,
        canStartDefense: Boolean,
        projectileHit: ModuleAutoDodge.HitInfo?,
    ) {
        val projectilePlan = projectileHit?.let {
            planEvasion(DodgePlannerConfig(allowRotations = AllowRotationChange.enabled), it)
        }
        val spearMovement = spearMovementController.update(
            canStartDefense,
            projectilePlan != null,
            player,
            world,
            Spear.movementSettings(),
        )
        val maceMovement = maceMovementController.update(
            canStartDefense,
            projectilePlan != null,
            player,
            world,
            Mace.movementSettings(),
        )
        primarySpearThreat = spearMovement.threat
        primaryMaceThreat = maceMovement.threat
        val action = AutoDodgeMovementArbitrator.chooseAction(
            projectilePlan,
            spearMovement.teleportPlan,
            spearMovement.jukePlan,
            maceMovement.teleportPlan,
        )
        AutoDodgeMovementExecutor.execute(
            event,
            action,
            spearMovement,
            ::performSpearTeleport,
            ::performMaceTeleport,
        )
    }

    private fun updatePacketDefense(
        canStartDefense: Boolean,
        projectileHit: ModuleAutoDodge.HitInfo?,
    ) {
        primarySpearThreat = spearMovementController.updateThreatOnly(
            canStartDefense,
            player,
            world,
            Spear.movementSettings(),
        )
        primaryMaceThreat = maceMovementController.updateThreatOnly(
            canStartDefense,
            player,
            world,
            Mace.movementSettings(),
        )
        packetController.update(packetRequest(projectileHit))
    }

    private fun packetRequest(projectileHit: ModuleAutoDodge.HitInfo?) = AutoDodgePacketUpdateRequest(
        player = player,
        world = world,
        cooldownTicks = Packet.cooldown,
        holdTicks = Packet.holdTicks,
        projectileThreat = projectileHit?.let {
            AutoDodgePacketProjectileThreat(it.tickDelta, it.prevArrowPos, it.arrowVelocity, it.arrowEntity.id)
        },
        maceThreat = primaryMaceThreat,
        spearThreat = primarySpearThreat,
        currentConnection = { mc.connection?.connection },
        blinkMovementQueued = { BlinkManager.isLagging },
        sendPacket = { sendPacketSilently(it) },
    )

    private fun performSpearTeleport(plan: SpearTeleportPlan) = spearMovementController.executeTeleport(
        player,
        world,
        plan,
        Spear.teleport.settings(),
        sendPacket = { sendPacketSilently(it) },
    )

    private fun performMaceTeleport(plan: SpearTeleportPlan) = maceMovementController.executeTeleport(
        player,
        world,
        plan,
        Mace.teleport.settings(),
        sendPacket = { sendPacketSilently(it) },
    )

    fun resetSpearMovement() {
        spearMovementController.resetMovement()
        primarySpearThreat = null
    }

    fun resetSpearTeleport() = spearMovementController.resetTeleport()

    fun resetMaceMovement() {
        maceMovementController.resetMovement()
        primaryMaceThreat = null
    }

    fun resetMaceTeleport() = maceMovementController.resetTeleport()

    fun enterPacketMode() {
        resetSpearMovement()
        resetSpearTeleport()
        resetMaceMovement()
        resetMaceTeleport()
        resetPacketRuntime()
    }

    fun resetPacketRuntime(returnToOrigin: Boolean = true) {
        val sendReturn = if (returnToOrigin && mc.connection?.connection?.isConnected == true) {
            { packet: ServerboundMovePlayerPacket.Pos -> sendPacketSilently(packet) }
        } else {
            null
        }
        packetController.reset(sendReturn)
    }

    fun resetAll(returnToOrigin: Boolean = true) {
        resetSpearMovement()
        resetSpearTeleport()
        resetMaceMovement()
        resetMaceTeleport()
        resetPacketRuntime(returnToOrigin)
    }

    fun findAvoidingArrowPosition() = projectileRuntime.findAvoidingArrowPosition()

    fun getInflictedHit(pos: Vec3) = projectileRuntime.getInflictedHit(pos)
}
