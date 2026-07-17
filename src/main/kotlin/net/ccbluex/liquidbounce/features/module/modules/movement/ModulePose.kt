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

package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.additions.forceSneak
import net.ccbluex.liquidbounce.additions.forceSprint
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.Pose

/**
 * Forces the local or server-derived player pose without changing normal movement input.
 */
object ModulePose : ClientModule("Pose", ModuleCategories.MOVEMENT) {

    private val mode by enumChoice("Mode", ForcedPose.CROUCHING).apply { tagBy(this) }
    private val side by enumChoice("Side", PoseSide.CLIENT)
    private val ignoreMovement by boolean("IgnoreMovement", false)

    fun modifyDesiredPose(original: Pose): Pose {
        if (!running) {
            return original
        }

        return resolveClientPose(side, mode, original)
    }

    fun modifyCrawlingForMovement(visuallyCrawling: Boolean): Boolean =
        applyPoseMovementSlowdown(running && ignoreMovement, visuallyCrawling)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? ServerboundPlayerInputPacket ?: return@handler

        if (side != PoseSide.SERVER) {
            return@handler
        }

        when (mode) {
            ForcedPose.CROUCHING -> {
                val dimensions = player.getDimensions(Pose.CROUCHING)
                val canFitCrouching = world.noCollision(
                    player,
                    dimensions.makeBoundingBox(player.position()).deflate(COLLISION_EPSILON),
                )

                if (shouldForceServerCrouching(canFitCrouching)) {
                    packet.forceSneak = true
                }
            }

            ForcedPose.SWIMMING -> {
                if (shouldForceServerSwimming(player.isUnderWater)) {
                    packet.forceSprint = true
                }
            }
        }
    }

    private const val COLLISION_EPSILON = 1.0E-7
}

internal enum class PoseSide(override val tag: String) : Tagged {
    CLIENT("Client"),
    SERVER("Server"),
}

internal enum class ForcedPose(override val tag: String, val minecraftPose: Pose) : Tagged {
    CROUCHING("Crouching", Pose.CROUCHING),
    SWIMMING("Swimming", Pose.SWIMMING),
}

internal fun resolveClientPose(side: PoseSide, mode: ForcedPose, original: Pose): Pose =
    if (side == PoseSide.CLIENT) mode.minecraftPose else original

internal fun shouldForceServerCrouching(canFitCrouching: Boolean): Boolean = canFitCrouching

internal fun shouldForceServerSwimming(isUnderWater: Boolean): Boolean = isUnderWater

internal fun applyPoseMovementSlowdown(ignoreMovement: Boolean, visuallyCrawling: Boolean): Boolean =
    visuallyCrawling && !ignoreMovement
