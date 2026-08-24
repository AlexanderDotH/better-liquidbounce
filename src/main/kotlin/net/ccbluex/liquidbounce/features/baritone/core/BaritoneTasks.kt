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

package net.ccbluex.liquidbounce.features.baritone.core

private val NAMESPACED_ID_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")

@JvmInline
value class BaritoneNamespacedId(val value: String) {
    init {
        require(NAMESPACED_ID_PATTERN.matches(value)) { "Expected a lowercase namespaced identifier" }
    }
}

enum class BaritoneTaskKind {
    GOTO,
    GET_TO_BLOCK,
    MINE,
    FOLLOW,
    FARM,
    EXPLORE,
    BUILD,
    ELYTRA,
}

sealed interface BaritoneGoal {

    data class Block(val position: BaritoneBlockPosition) : BaritoneGoal

    data class Horizontal(val position: BaritoneHorizontalPosition) : BaritoneGoal

    data class Level(val y: Int) : BaritoneGoal

    data class Near(
        val position: BaritoneBlockPosition,
        val radius: Int,
    ) : BaritoneGoal {
        init {
            require(radius > 0) { "Goal radius must be positive" }
        }
    }
}

sealed interface BaritoneTaskRequest {

    val kind: BaritoneTaskKind

    data class GoTo(val goal: BaritoneGoal) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.GOTO
    }

    data class GetToBlock(val block: BaritoneNamespacedId) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.GET_TO_BLOCK
    }

    class Mine(
        blocks: Collection<BaritoneNamespacedId>,
        val quantity: Int = 1,
    ) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.MINE
        val blocks: List<BaritoneNamespacedId> = immutableListCopy(blocks)

        init {
            require(this.blocks.isNotEmpty()) { "Mine tasks need at least one block" }
            require(quantity > 0) { "Mine quantity must be positive" }
        }

        override fun equals(other: Any?): Boolean =
            other is Mine && blocks == other.blocks && quantity == other.quantity

        override fun hashCode(): Int = 31 * blocks.hashCode() + quantity

        override fun toString(): String = "Mine(blocks=$blocks, quantity=$quantity)"
    }

    data class Follow(
        val player: String,
        val radius: Double = 2.0,
    ) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.FOLLOW

        init {
            require(player.isNotBlank()) { "Follow target cannot be blank" }
            require(radius.isFinite() && radius > 0.0) { "Follow radius must be positive and finite" }
        }
    }

    data class Farm(
        val center: BaritoneBlockPosition? = null,
        val radius: Int = 64,
    ) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.FARM

        init {
            require(radius > 0) { "Farm radius must be positive" }
        }
    }

    data class Explore(
        val origin: BaritoneHorizontalPosition? = null,
        val radius: Int? = null,
    ) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.EXPLORE

        init {
            require(radius == null || radius > 0) { "Explore radius must be positive" }
        }
    }

    data class Build(
        val schematic: String,
        val origin: BaritoneBlockPosition? = null,
    ) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.BUILD

        init {
            require(schematic.isNotBlank()) { "Schematic path cannot be blank" }
        }
    }

    data class Elytra(val destination: BaritoneBlockPosition) : BaritoneTaskRequest {
        override val kind = BaritoneTaskKind.ELYTRA
    }
}
