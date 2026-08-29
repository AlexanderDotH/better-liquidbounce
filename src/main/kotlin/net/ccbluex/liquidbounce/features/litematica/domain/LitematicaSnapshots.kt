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

package net.ccbluex.liquidbounce.features.litematica.domain

@JvmInline
value class LitematicaPlacementId(val value: String) {
    init {
        require(value.isNotBlank()) { "Litematica placement id must not be blank" }
    }
}

enum class LitematicaBlockKind {
    AIR,
    SOLID,
    FLUID_SOURCE,
    FLUID_FLOWING,
    UNSUPPORTED,
}

class LitematicaBlockSnapshot(
    val id: String,
    properties: Map<String, String> = emptyMap(),
    val kind: LitematicaBlockKind,
    val replaceable: Boolean,
    val hasBlockEntity: Boolean = false,
    val reproducible: Boolean = true,
) {
    val properties: Map<String, String> = properties.toMap()

    init {
        require(id.isNotBlank()) { "Litematica block id must not be blank" }
        require(properties.keys.none(String::isBlank)) { "Litematica block property names must not be blank" }
    }

    fun sameBlockAs(other: LitematicaBlockSnapshot): Boolean = id == other.id

    fun sameStateAs(other: LitematicaBlockSnapshot): Boolean = sameBlockAs(other) &&
        kind == other.kind && properties == other.properties

    companion object {
        fun air(id: String = "minecraft:air") = LitematicaBlockSnapshot(
            id = id,
            kind = LitematicaBlockKind.AIR,
            replaceable = true,
        )

        fun solid(
            id: String,
            properties: Map<String, String> = emptyMap(),
            replaceable: Boolean = false,
            hasBlockEntity: Boolean = false,
            reproducible: Boolean = true,
        ) = LitematicaBlockSnapshot(
            id = id,
            properties = properties,
            kind = LitematicaBlockKind.SOLID,
            replaceable = replaceable,
            hasBlockEntity = hasBlockEntity,
            reproducible = reproducible,
        )

        fun sourceFluid(id: String, properties: Map<String, String> = emptyMap()) = LitematicaBlockSnapshot(
            id = id,
            properties = properties,
            kind = LitematicaBlockKind.FLUID_SOURCE,
            replaceable = false,
        )

        fun flowingFluid(id: String, properties: Map<String, String> = emptyMap()) = LitematicaBlockSnapshot(
            id = id,
            properties = properties,
            kind = LitematicaBlockKind.FLUID_FLOWING,
            replaceable = false,
            reproducible = false,
        )

        fun unsupported(id: String, properties: Map<String, String> = emptyMap()) = LitematicaBlockSnapshot(
            id = id,
            properties = properties,
            kind = LitematicaBlockKind.UNSUPPORTED,
            replaceable = false,
            reproducible = false,
        )
    }
}

enum class LitematicaPlacementMethod {
    NEIGHBOR_FACE,
    AIR_PLACE,
    UNAVAILABLE,
}

data class LitematicaCellSnapshot(
    val position: LitematicaPosition,
    val desired: LitematicaBlockSnapshot,
    val actual: LitematicaBlockSnapshot,
    val placementMethod: LitematicaPlacementMethod = LitematicaPlacementMethod.UNAVAILABLE,
    val materialAvailable: Boolean = true,
    val requiredMaterialId: String? = null,
) {
    init {
        require(requiredMaterialId == null || requiredMaterialId.isNotBlank()) {
            "Required Litematica material id must not be blank"
        }
        require(materialAvailable || requiredMaterialId != null) {
            "Unavailable Litematica material must expose its required material id"
        }
    }
}

enum class LitematicaAxis {
    X,
    Y,
    Z,
}

data class LitematicaRenderLayer(
    val axis: LitematicaAxis?,
    val minimum: Int?,
    val maximum: Int?,
) {
    init {
        require(axis != null || minimum == null && maximum == null) {
            "An unbounded Litematica render layer must not declare coordinates"
        }
        require(minimum == null || maximum == null || minimum <= maximum) {
            "Litematica render layer minimum must not exceed maximum"
        }
    }

    constructor(minY: Int? = null, maxY: Int? = null) : this(LitematicaAxis.Y, minY, maxY)

    fun allows(position: LitematicaPosition): Boolean {
        val coordinate = when (axis) {
            LitematicaAxis.X -> position.x
            LitematicaAxis.Y -> position.y
            LitematicaAxis.Z -> position.z
            null -> return true
        }
        return (minimum == null || coordinate >= minimum) && (maximum == null || coordinate <= maximum)
    }

    companion object {
        val ALL = LitematicaRenderLayer(axis = null, minimum = null, maximum = null)
    }
}

class LitematicaPlacementSnapshot(
    val id: LitematicaPlacementId,
    val name: String,
    val enabled: Boolean,
    val rendered: Boolean,
    val bounds: LitematicaBounds,
    val renderLayer: LitematicaRenderLayer = LitematicaRenderLayer.ALL,
    cells: Collection<LitematicaCellSnapshot>,
) {
    val cells: List<LitematicaCellSnapshot> = cells.toList()

    init {
        require(name.isNotBlank()) { "Litematica placement name must not be blank" }
        require(this.cells.all { bounds.contains(it.position) }) {
            "Every Litematica placement cell must be inside its placement bounds"
        }
    }
}
