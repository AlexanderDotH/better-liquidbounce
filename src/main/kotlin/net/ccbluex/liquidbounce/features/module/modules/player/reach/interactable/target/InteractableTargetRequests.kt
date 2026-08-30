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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeTarget

@JvmRecord
internal data class InteractablePlayerEligibility(
    val alive: Boolean = true,
    val spectator: Boolean = false,
    val passenger: Boolean = false,
    val detachedCamera: Boolean = false,
    val remoteMovementAvailable: Boolean = true,
)

@JvmRecord
internal data class InteractableTargetRequest(
    val maxRange: Double,
    val normalInteractionAvailable: Boolean,
    val player: InteractablePlayerEligibility,
    val containerVehicles: Boolean,
    val blockFilter: InteractableTargetBlockFilter,
)

@JvmRecord
internal data class InteractableResolvedTarget(
    val lock: InteractableTargetLock,
    val initialHitLocation: InteractableTargetPoint,
    val distanceSquared: Double,
) : InteractableRuntimeTarget

internal sealed interface InteractableTargetResolution {
    @JvmRecord
    data class Acquired(val target: InteractableResolvedTarget) : InteractableTargetResolution

    @JvmRecord
    data class Rejected(val reason: InteractableTargetRejection) : InteractableTargetResolution
}

internal sealed interface InteractableTargetValidation {
    data object Valid : InteractableTargetValidation

    @JvmRecord
    data class Invalid(val reason: InteractableTargetRejection) : InteractableTargetValidation
}

internal enum class InteractableTargetRejection {
    NORMAL_INTERACTION_PRIORITY,
    INVALID_RANGE,
    PLAYER_DEAD,
    SPECTATOR,
    PASSENGER,
    DETACHED_CAMERA,
    REMOTE_MOVEMENT_BUSY,
    WORLD_UNAVAILABLE,
    MISS,
    OCCLUDED,
    OUT_OF_RANGE,
    UNLOADED,
    OUTSIDE_WORLD_BORDER,
    BLOCKED,
    NOT_MENU_PROVIDER,
    FILTERED,
    CONTAINER_VEHICLES_DISABLED,
    UNSUPPORTED_ENTITY,
    TARGET_REMOVED,
    TARGET_CHANGED,
    TARGET_MISSING,
    INVALID_TARGET,
}
