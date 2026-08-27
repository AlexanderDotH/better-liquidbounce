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

internal interface InteractableTargetWorldAdapter {
    fun raycast(maxRange: Double): InteractableRayHit

    fun observe(lock: InteractableTargetLock): InteractableTargetObservation
}

internal class InteractableTargetResolver(
    private val worldAdapter: InteractableTargetWorldAdapter,
) {

    fun acquire(request: InteractableTargetRequest): InteractableTargetResolution {
        request.preRaycastRejection()?.let { return it.rejected() }

        return when (val hit = worldAdapter.raycast(request.maxRange)) {
            InteractableRayHit.Miss -> InteractableTargetRejection.MISS.rejected()
            InteractableRayHit.WorldUnavailable -> InteractableTargetRejection.WORLD_UNAVAILABLE.rejected()
            is InteractableRayHit.Block -> acquireBlock(request, hit)
            is InteractableRayHit.Entity -> acquireEntity(request, hit)
        }
    }

    fun validate(lock: InteractableTargetLock): InteractableTargetValidation {
        return when (val observation = worldAdapter.observe(lock)) {
            InteractableTargetObservation.Missing -> InteractableTargetRejection.TARGET_MISSING.invalid()
            InteractableTargetObservation.WorldUnavailable -> InteractableTargetRejection.WORLD_UNAVAILABLE.invalid()
            is InteractableTargetObservation.Block -> validateBlock(lock, observation)
            is InteractableTargetObservation.Entity -> validateEntity(lock, observation)
        }
    }

    private fun acquireBlock(
        request: InteractableTargetRequest,
        hit: InteractableRayHit.Block,
    ): InteractableTargetResolution {
        val observation = hit.observation
        val rejection = hit.candidateRejection(request.maxRange) ?: observation.blockRejection()
        rejection?.let { return it.rejected() }
        val identity = requireNotNull(observation.identity)
        if (!request.blockFilter.allows(identity.blockKey)) {
            return InteractableTargetRejection.FILTERED.rejected()
        }

        return InteractableTargetResolution.Acquired(hit.resolved(observation.toLock()))
    }

    private fun acquireEntity(
        request: InteractableTargetRequest,
        hit: InteractableRayHit.Entity,
    ): InteractableTargetResolution {
        val observation = hit.observation
        val rejection = hit.candidateRejection(request.maxRange) ?: when {
            !observation.kind.isSupportedContainerVehicle -> InteractableTargetRejection.UNSUPPORTED_ENTITY
            !request.containerVehicles -> InteractableTargetRejection.CONTAINER_VEHICLES_DISABLED
            else -> observation.entityRejection()
        }
        return rejection?.rejected()
            ?: InteractableTargetResolution.Acquired(hit.resolved(observation.toLock()))
    }

    private fun validateBlock(
        lock: InteractableTargetLock,
        observation: InteractableTargetObservation.Block,
    ): InteractableTargetValidation {
        val blockLock = lock as? InteractableTargetLock.Block
            ?: return InteractableTargetRejection.TARGET_CHANGED.invalid()
        observation.persistentBlockRejection()?.let { return it.invalid() }
        if (observation.position != blockLock.position || observation.identity != blockLock.identity) {
            return InteractableTargetRejection.TARGET_CHANGED.invalid()
        }
        observation.menuRejection()?.let { return it.invalid() }
        return InteractableTargetValidation.Valid
    }

    private fun validateEntity(
        lock: InteractableTargetLock,
        observation: InteractableTargetObservation.Entity,
    ): InteractableTargetValidation {
        val entityLock = lock as? InteractableTargetLock.ContainerVehicle
            ?: return InteractableTargetRejection.TARGET_CHANGED.invalid()
        observation.entityRejection()?.let { return it.invalid() }
        if (observation.uuid != entityLock.uuid || observation.kind != entityLock.kind) {
            return InteractableTargetRejection.TARGET_CHANGED.invalid()
        }
        return InteractableTargetValidation.Valid
    }
}

private fun InteractableTargetRequest.preRaycastRejection(): InteractableTargetRejection? {
    if (normalInteractionAvailable) return InteractableTargetRejection.NORMAL_INTERACTION_PRIORITY
    if (!maxRange.isFinite() || maxRange <= 0.0) return InteractableTargetRejection.INVALID_RANGE
    if (!player.alive) return InteractableTargetRejection.PLAYER_DEAD
    if (player.spectator) return InteractableTargetRejection.SPECTATOR
    if (player.passenger) return InteractableTargetRejection.PASSENGER
    if (player.detachedCamera) return InteractableTargetRejection.DETACHED_CAMERA
    if (!player.remoteMovementAvailable) return InteractableTargetRejection.REMOTE_MOVEMENT_BUSY
    return null
}

private fun InteractableRayHit.Candidate.candidateRejection(maxRange: Double): InteractableTargetRejection? {
    if (!visible) return InteractableTargetRejection.OCCLUDED
    if (!hitLocation.isFinite || !distanceSquared.isFinite() || distanceSquared < 0.0) {
        return InteractableTargetRejection.INVALID_TARGET
    }
    if (distanceSquared > maxRange * maxRange) return InteractableTargetRejection.OUT_OF_RANGE
    return null
}

private fun InteractableTargetObservation.Block.blockRejection(): InteractableTargetRejection? {
    persistentBlockRejection()?.let { return it }
    return menuRejection()
}

private fun InteractableTargetObservation.Block.persistentBlockRejection(): InteractableTargetRejection? {
    if (!loaded) return InteractableTargetRejection.UNLOADED
    if (!insideWorldBorder) return InteractableTargetRejection.OUTSIDE_WORLD_BORDER
    if (identity == null) return InteractableTargetRejection.INVALID_TARGET
    return null
}

private fun InteractableTargetObservation.Block.menuRejection(): InteractableTargetRejection? {
    if (blocked) return InteractableTargetRejection.BLOCKED
    if (!menuProviderAvailable) return InteractableTargetRejection.NOT_MENU_PROVIDER
    return null
}

private fun InteractableTargetObservation.Entity.entityRejection(): InteractableTargetRejection? {
    if (!loaded) return InteractableTargetRejection.UNLOADED
    if (!insideWorldBorder) return InteractableTargetRejection.OUTSIDE_WORLD_BORDER
    if (removed || !alive) return InteractableTargetRejection.TARGET_REMOVED
    return null
}

private fun InteractableRayHit.Candidate.resolved(lock: InteractableTargetLock) = InteractableResolvedTarget(
    lock = lock,
    initialHitLocation = hitLocation,
    distanceSquared = distanceSquared,
)

private fun InteractableTargetRejection.rejected() = InteractableTargetResolution.Rejected(this)

private fun InteractableTargetRejection.invalid() = InteractableTargetValidation.Invalid(this)
