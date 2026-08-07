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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.minecraft.core.BlockPos
import kotlin.math.max

internal enum class BaseSignalFamily(
    val maximumScore: Int,
    val seedCapable: Boolean,
) {
    STORAGE(30, true),
    UTILITIES(18, true),
    AUTOMATION(20, true),
    ENTITIES(12, true),
    STRUCTURAL(12, true),
    GEOMETRY(10, true),
    COMPACT_BASE(32, false),
    ACTIVITY(6, false),
    CHUNK_TRAILS(4, false),
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

internal enum class BaseFinderBoxMode(override val tag: String) : Tagged {
    FIXED("Fixed"),
    DYNAMIC("Dynamic box"),
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

internal data class StorageSignal(
    val weightedPoints: Int = 0,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class UtilitiesSignal(
    val categories: Set<String> = emptySet(),
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class AutomationSignal(
    val diversityPoints: Int = 0,
    val densityPoints: Int = 0,
    val organizedPattern: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class EntitiesSignal(
    val diversityPoints: Int = 0,
    val densityPoints: Int = 0,
    val hasContainerVehicleOrChestedMount: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class StructuralSignal(
    val portalShape: Boolean = false,
    val bedGroup: Boolean = false,
    val infrastructure: Boolean = false,
    val decorationCluster: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class GeometrySignal(
    val caveDisturbance: Boolean = false,
    val artificialPattern: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class ActivitySignal(
    val repeatedCategories: Int = 0,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class ChunkTrailsSignal(
    val boundary: Boolean = false,
    val trailEndpoint: Boolean = false,
    val anchors: List<EvidenceAnchor> = emptyList(),
)

internal data class ChunkEvidenceSnapshot(
    val chunk: ChunkCoordinate,
    val storage: StorageSignal = StorageSignal(),
    val utilities: UtilitiesSignal = UtilitiesSignal(),
    val automation: AutomationSignal = AutomationSignal(),
    val entities: EntitiesSignal = EntitiesSignal(),
    val structural: StructuralSignal = StructuralSignal(),
    val geometry: GeometrySignal = GeometrySignal(),
    val activity: ActivitySignal = ActivitySignal(),
    val chunkTrails: ChunkTrailsSignal = ChunkTrailsSignal(),
    val falsePositives: Set<BaseFalsePositive> = emptySet(),
    val dimensionKey: String = "minecraft:overworld",
)

internal interface BaseDetectionStrategy {
    val family: BaseSignalFamily

    fun evaluate(snapshot: ChunkEvidenceSnapshot): FamilyEvidence?
}

internal data class FamilyEvidence(
    val family: BaseSignalFamily,
    val score: Int,
    val anchors: List<EvidenceAnchor>,
    val keys: List<String>,
)

internal data class EvidenceSummary(
    val family: BaseSignalFamily,
    val score: Int,
    val keys: List<String>,
)

internal data class ScoredBaseCandidate(
    val anchor: BaseCoordinate,
    val confidence: Int,
    val tier: ConfidenceTier,
    val evidence: List<EvidenceSummary>,
    val chunks: Set<ChunkCoordinate>,
    val accepted: Boolean,
    val bounds: BaseFinderBounds? = null,
)

internal data class BaseFinding(
    val id: String,
    val serverKeyHash: String,
    val dimensionKey: String,
    val anchor: BaseCoordinate,
    val confidence: Int,
    val tier: ConfidenceTier,
    val evidence: List<EvidenceSummary>,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val timesSeen: Int,
    val bounds: BaseFinderBounds? = null,
)

/** Localized, render-only representation of one persisted evidence family. */
internal data class BaseFinderLabelEvidence(
    val family: String,
    val score: Int,
    val detections: List<String>,
)

internal data class BaseFinderMarker(
    val id: String,
    val anchor: BaseCoordinate,
    val confidence: Int,
    val topEvidenceKeys: List<String>,
    val updatedAtMillis: Long,
    val evidenceDetails: List<BaseFinderLabelEvidence> = emptyList(),
    val bounds: BaseFinderBounds? = null,
)

internal data class BaseFinderRenderSnapshot(
    val worldEpoch: Long,
    val serverKey: String,
    val dimensionKey: String,
    val revision: Long,
    val markers: List<BaseFinderMarker>,
)
