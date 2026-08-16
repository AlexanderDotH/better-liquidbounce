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
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

internal data class MaceMovementSettings(
    val enabled: Boolean,
    val packetThreatRange: Double,
    val threatMemoryTicks: Int,
    val teleportEnabled: Boolean,
    val teleport: SpearTeleportSettings,
)

internal data class MaceMovementResult(
    val threat: MaceThreat?,
    val teleportPlan: SpearTeleportPlan?,
)

/** Coordinates packet-capable mace detection with the shared collision-safe emergency teleport engine. */
internal class MaceMovementController(
    private val threatDetector: MaceThreatDetector = MaceThreatDetector(),
    private val teleportRuntime: SpearTeleportRuntime = SpearTeleportRuntime(),
) {
    var primaryThreat: MaceThreat? = null
        private set

    val teleportState: SpearTeleportState
        get() = teleportRuntime.state

    val plannedTeleport: SpearTeleportPlan?
        get() = teleportRuntime.plannedTeleport

    fun update(
        canStartDefense: Boolean,
        projectilePlanActive: Boolean,
        player: LocalPlayer,
        world: ClientLevel,
        settings: MaceMovementSettings,
    ): MaceMovementResult {
        primaryThreat = detectThreat(canStartDefense, player, world, settings)
        val teleportPlan = teleportRuntime.planMace(
            enabled = settings.enabled && settings.teleportEnabled,
            canStartDefense = canStartDefense && !projectilePlanActive,
            projectilePlanActive = projectilePlanActive,
            tick = player.tickCount.toLong(),
            playerPosition = player.position(),
            threat = primaryThreat,
            settings = settings.teleport,
            isSafe = { candidate -> isSafeSpearTeleportCandidate(world, player, settings.teleport, candidate) },
        )
        return MaceMovementResult(primaryThreat, teleportPlan)
    }

    fun executeTeleport(
        player: LocalPlayer,
        world: ClientLevel,
        plan: SpearTeleportPlan,
        settings: SpearTeleportSettings,
        sendPacket: (ServerboundMovePlayerPacket) -> Unit,
    ): Boolean {
        val origin = player.position()
        return teleportRuntime.execute(
            tick = player.tickCount.toLong(),
            from = origin,
            plan = plan,
            settings = settings,
            onGround = player.onGround() && plan.destination.y == origin.y,
            horizontalCollision = player.horizontalCollision,
            isStillSafe = { isSafeSpearTeleportCandidate(world, player, settings, plan.destination) },
            sendPacket = sendPacket,
            moveLocalPlayer = { destination ->
                player.setPos(destination)
                player.deltaMovement = Vec3.ZERO
            },
        )
    }

    fun resetMovement() {
        threatDetector.reset()
        primaryThreat = null
    }

    fun resetTeleport() {
        teleportRuntime.reset()
    }

    private fun detectThreat(
        canStartDefense: Boolean,
        player: LocalPlayer,
        world: ClientLevel,
        settings: MaceMovementSettings,
    ): MaceThreat? {
        if (!settings.enabled || !canStartDefense) {
            resetMovement()
            return null
        }

        return threatDetector.update(
            targetPosition = player.position(),
            candidates = world.players().asSequence()
                .filterIsInstance<RemotePlayer>()
                .map { it.toMaceThreatCandidate() }
                .asIterable(),
            packetThreatRange = settings.packetThreatRange,
            threatMemoryTicks = settings.threatMemoryTicks,
        )
    }

    private fun RemotePlayer.toMaceThreatCandidate() = MaceThreatCandidate(
        entityId = id,
        name = scoreboardName,
        position = position(),
        lookDirection = lookAngle,
        isHoldingMace = mainHandItem.`is`(Items.MACE) || offhandItem.`is`(Items.MACE),
        isAlive = isAlive,
        isRemoved = isRemoved,
        isBot = ModuleAntiBot.isBot(this),
    )
}
