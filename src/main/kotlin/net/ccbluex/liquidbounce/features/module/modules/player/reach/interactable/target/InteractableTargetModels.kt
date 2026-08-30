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

import java.util.UUID

@JvmRecord
internal data class InteractableTargetPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    val isFinite: Boolean
        get() = x.isFinite() && y.isFinite() && z.isFinite()
}

@JvmRecord
internal data class InteractableBlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
)

@JvmInline
internal value class InteractableBlockKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Block key must not be blank" }
    }
}

@JvmInline
internal value class InteractableBlockStateKey(val value: Int)

@JvmRecord
internal data class InteractableBlockIdentity(
    val blockKey: InteractableBlockKey,
    val stateKey: InteractableBlockStateKey,
)

internal enum class InteractableEntityKind {
    CONTAINER_MINECART,
    CHEST_BOAT,
    CHEST_RAFT,
    UNSUPPORTED;

    val isSupportedContainerVehicle: Boolean
        get() = this != UNSUPPORTED
}

internal sealed interface InteractableTargetLock {

    @JvmRecord
    data class Block(
        val position: InteractableBlockPosition,
        val identity: InteractableBlockIdentity,
    ) : InteractableTargetLock

    @JvmRecord
    data class ContainerVehicle(
        val uuid: UUID,
        val kind: InteractableEntityKind,
        val position: InteractableTargetPoint,
    ) : InteractableTargetLock {
        init {
            require(kind.isSupportedContainerVehicle) { "Unsupported entities cannot be locked" }
        }
    }
}

internal sealed interface InteractableTargetObservation {

    data object Missing : InteractableTargetObservation

    data object WorldUnavailable : InteractableTargetObservation

    @JvmRecord
    data class Block(
        val position: InteractableBlockPosition,
        val identity: InteractableBlockIdentity?,
        val loaded: Boolean,
        val insideWorldBorder: Boolean,
        val menuProviderAvailable: Boolean,
        val blocked: Boolean,
    ) : InteractableTargetObservation {
        fun toLock() = InteractableTargetLock.Block(position, requireNotNull(identity))
    }

    @JvmRecord
    data class Entity(
        val uuid: UUID,
        val kind: InteractableEntityKind,
        val alive: Boolean,
        val removed: Boolean,
        val loaded: Boolean,
        val insideWorldBorder: Boolean,
        val position: InteractableTargetPoint,
    ) : InteractableTargetObservation {
        fun toLock() = InteractableTargetLock.ContainerVehicle(uuid, kind, position)
    }
}

internal sealed interface InteractableRayHit {

    data object Miss : InteractableRayHit

    data object WorldUnavailable : InteractableRayHit

    sealed interface Candidate : InteractableRayHit {
        val hitLocation: InteractableTargetPoint
        val distanceSquared: Double
        val visible: Boolean
    }

    @JvmRecord
    data class Block(
        val observation: InteractableTargetObservation.Block,
        override val hitLocation: InteractableTargetPoint,
        override val distanceSquared: Double,
        override val visible: Boolean,
    ) : Candidate

    @JvmRecord
    data class Entity(
        val observation: InteractableTargetObservation.Entity,
        override val hitLocation: InteractableTargetPoint,
        override val distanceSquared: Double,
        override val visible: Boolean,
    ) : Candidate
}

internal enum class InteractableBlockFilterMode {
    BLACKLIST,
    WHITELIST,
}

internal class InteractableTargetBlockFilter private constructor(
    private val mode: InteractableBlockFilterMode,
    blockKeys: Set<InteractableBlockKey>,
) {
    private val blockKeys = blockKeys.toSet()

    fun allows(blockKey: InteractableBlockKey): Boolean = when (mode) {
        InteractableBlockFilterMode.BLACKLIST -> blockKey !in blockKeys
        InteractableBlockFilterMode.WHITELIST -> blockKey in blockKeys
    }

    companion object {
        fun blacklist(blockKeys: Set<InteractableBlockKey> = emptySet()) =
            InteractableTargetBlockFilter(InteractableBlockFilterMode.BLACKLIST, blockKeys)

        fun whitelist(blockKeys: Set<InteractableBlockKey>) =
            InteractableTargetBlockFilter(InteractableBlockFilterMode.WHITELIST, blockKeys)
    }
}
