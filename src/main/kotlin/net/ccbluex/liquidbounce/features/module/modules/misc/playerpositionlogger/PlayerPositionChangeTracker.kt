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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

@JvmRecord
internal data class PlayerPositionIdentity(
    val entityId: Int,
    val uuid: String,
    val name: String,
    val local: Boolean,
)

@JvmRecord
internal data class PlayerPositionSample(
    val identity: PlayerPositionIdentity,
    val state: PlayerPositionState,
)

@JvmRecord
internal data class PlayerPositionStateChange(
    val kind: PlayerPositionLogKind,
    val sample: PlayerPositionSample,
    val previousState: PlayerPositionState? = null,
)

internal class PlayerPositionChangeTracker {

    private val previousSamples = mutableMapOf<Int, PlayerPositionSample>()

    fun update(samples: Collection<PlayerPositionSample>): List<PlayerPositionStateChange> {
        val currentSamples = samples.associateBy { it.identity.entityId }
        val changes = samples.mapNotNull { sample ->
            changeFor(sample, previousSamples[sample.identity.entityId])
        }.toMutableList()

        previousSamples.values
            .filter { it.identity.entityId !in currentSamples }
            .mapTo(changes) { PlayerPositionStateChange(PlayerPositionLogKind.STATE_REMOVED, it) }

        previousSamples.clear()
        previousSamples.putAll(currentSamples)
        return changes
    }

    fun clear() {
        previousSamples.clear()
    }

    private fun changeFor(
        sample: PlayerPositionSample,
        previous: PlayerPositionSample?,
    ): PlayerPositionStateChange? = when {
        previous == null -> PlayerPositionStateChange(PlayerPositionLogKind.STATE_INITIAL, sample)
        previous.state != sample.state -> PlayerPositionStateChange(
            PlayerPositionLogKind.STATE_CHANGED,
            sample,
            previous.state,
        )
        else -> null
    }
}
