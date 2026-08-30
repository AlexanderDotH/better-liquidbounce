/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder.model

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import kotlin.math.max

internal enum class BaseSignalFamily(
    val maximumScore: Int,
    val seedCapable: Boolean,
    val showFamilyScore: Boolean = true,
) {
    STORAGE(30, true),
    UTILITIES(18, true),
    AUTOMATION(20, true),
    ENTITIES(12, true),
    STRUCTURAL(12, true),
    GEOMETRY(10, true),
    SEED_MISMATCH(65, true, showFamilyScore = false),
    COMPACT_BASE(32, false),
    ACTIVITY(6, false),
    CHUNK_TRAILS(4, false),
}

/** Which generation stage produced a seed-mismatch signal. */
internal enum class SeedComparePhase {
    NONE,
    SPARSE,
    /** Dense compare of a limited local neighborhood (player overlay), not the whole chunk. */
    OVERLAY,
    FULL,
}

/** Classification of one seed-expected vs observed block cell. */
internal enum class SeedMismatchKind {
    MISSING_SOLID,
    UNEXPECTED_SOLID,
    UTILITY,

    /**
     * Both cells are solid, but they are different materials — cobblestone where the seed says stone.
     * Overlay-only: material swaps never feed [SeedMismatchSignal.mismatchRatio] or base scoring.
     */
    MATERIAL_SWAP,
}

/** One mismatched block cell used for scoring anchors and the live mismatch overlay. */
internal data class SeedMismatchCell(
    val position: BaseCoordinate,
    val kind: SeedMismatchKind,
    /** Block in the loaded world. */
    val observedBlockId: Int = UNKNOWN_BLOCK_ID,
    /** Block rebuilt from the supplied seed. */
    val expectedBlockId: Int = UNKNOWN_BLOCK_ID,
) {
    /** Compact diagnostic for the closest outlined cell in ModuleDebug. */
    fun debugDescription(): String =
        "${position.x} ${position.y} ${position.z} ${kind.name.lowercase().replace('_', ' ')}: " +
            "actual=${BaseFinderBlockRegistry.nameOf(observedBlockId)} " +
            "expected=${BaseFinderBlockRegistry.nameOf(expectedBlockId)}"

    private companion object {
        const val UNKNOWN_BLOCK_ID = -1
    }
}

internal enum class BaseFalsePositive(val penalty: Int) {
    VILLAGE(30),
    MINESHAFT_OR_DUNGEON(25),
    RUINED_PORTAL(20),
    FORTRESS_BASTION_OR_END_CITY(25),
    ISOLATED_GENERATED_LOOT_CONTAINER(20),
    HOMOGENEOUS_SIGNAL(15),
}

internal enum class ConfidenceTier {
    POSSIBLE,
    LIKELY,
    STRONG;

    companion object {
        fun from(confidence: Int) = when {
            confidence >= 90 -> STRONG
            confidence >= 75 -> LIKELY
            else -> POSSIBLE
        }
    }
}

/** Integer world position which cannot retain a scanner-owned mutable [BlockPos]. */
internal data class BaseCoordinate(val x: Int, val y: Int, val z: Int) {
    val blockPos: BlockPos
        get() = BlockPos(x, y, z)

    val chunk: ChunkCoordinate
        get() = ChunkCoordinate(Math.floorDiv(x, 16), Math.floorDiv(z, 16))

    companion object {
        fun of(position: BlockPos) = BaseCoordinate(position.x, position.y, position.z)
    }
}

/** Inclusive block-coordinate bounds for a detected, stationary base footprint. */
internal data class BaseFinderBounds(
    val minimum: BaseCoordinate,
    val maximum: BaseCoordinate,
) {
    init {
        requireValid()
    }

    fun merge(other: BaseFinderBounds) = BaseFinderBounds(
        minimum = BaseCoordinate(
            minOf(minimum.x, other.minimum.x),
            minOf(minimum.y, other.minimum.y),
            minOf(minimum.z, other.minimum.z),
        ),
        maximum = BaseCoordinate(
            maxOf(maximum.x, other.maximum.x),
            maxOf(maximum.y, other.maximum.y),
            maxOf(maximum.z, other.maximum.z),
        ),
    )

    fun requireValid() {
        require(minimum.x <= maximum.x)
        require(minimum.y <= maximum.y)
        require(minimum.z <= maximum.z)
    }

    companion object {
        fun enclosing(positions: Iterable<BaseCoordinate>): BaseFinderBounds? {
            val iterator = positions.iterator()
            if (!iterator.hasNext()) return null

            val first = iterator.next()
            var minimum = first
            var maximum = first
            while (iterator.hasNext()) {
                val position = iterator.next()
                minimum = BaseCoordinate(
                    minOf(minimum.x, position.x),
                    minOf(minimum.y, position.y),
                    minOf(minimum.z, position.z),
                )
                maximum = BaseCoordinate(
                    maxOf(maximum.x, position.x),
                    maxOf(maximum.y, position.y),
                    maxOf(maximum.z, position.z),
                )
            }
            return BaseFinderBounds(minimum, maximum)
        }
    }
}

internal data class ChunkCoordinate(val x: Int, val z: Int) {
    fun chebyshevDistance(other: ChunkCoordinate): Int = max(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    fun pack(): Long = ChunkPos.pack(x, z)

    companion object {
        fun unpack(packed: Long): ChunkCoordinate =
            ChunkCoordinate(ChunkPos.getX(packed), ChunkPos.getZ(packed))
    }
}

internal data class EvidenceAnchor(
    val position: BaseCoordinate,
    val weight: Int,
    val key: String,
) {
    init {
        require(weight >= 0) { "Evidence anchor weight must be non-negative" }
    }

    companion object {
        fun of(position: BlockPos, weight: Int, key: String) =
            EvidenceAnchor(BaseCoordinate.of(position), weight, key)
    }
}
