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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.structures

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.ChunkCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceConfidence
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.StructureType
import java.util.Collections

/**
 * Detects only client-visible, multi-block structure signatures. It never receives or queries server-side structure
 * metadata. A complete compact template is [EvidenceConfidence.STRONG]; a partial multi-feature template is
 * [EvidenceConfidence.AMBIGUOUS] and must be confirmed by the player before a solver can use it.
 */
internal object StructureSignatureDetector {

    fun detect(snapshot: StructureChunkSnapshot): List<StructureSignatureMatch> {
        if (snapshot.dimensionKey != OVERWORLD_DIMENSION) return emptyList()

        return rules.mapNotNull { rule -> rule.detect(snapshot) }
    }

    private fun SignatureRule.detect(snapshot: StructureChunkSnapshot): StructureSignatureMatch? {
        val featureMatches = features.mapNotNull { feature ->
            feature.match(snapshot.blocks).takeIf { it.blocks.isNotEmpty() }
        }
        val minimumFeatures = maxOf(MINIMUM_FEATURES_FOR_AMBIGUOUS_EVIDENCE, features.size - 1)
        if (featureMatches.size < minimumFeatures) return null

        val complete = featureMatches.size == features.size
        val compact = featureMatches.isCompact(maximumHorizontalSpan)
        val geometryMatches = geometry(featureMatches.associateBy(FeatureMatch::key))
        if (!compact || !geometryMatches) return null
        val confidence = if (complete && compact && geometryMatches) {
            EvidenceConfidence.STRONG
        } else {
            EvidenceConfidence.AMBIGUOUS
        }

        val matchedBlocks = featureMatches.flatMap(FeatureMatch::blocks).distinct()
        val anchor = matchedBlocks.anchorChunk()
        return StructureSignatureMatch(
            type = type,
            confidence = confidence,
            anchorChunk = anchor,
            snapshotHash = snapshot.snapshotHash,
            matchedFeatureKeys = featureMatches.map(FeatureMatch::key).immutableSortedSet(),
            matchedBlockIds = matchedBlocks.map(StructureBlockSnapshot::blockId).immutableSortedSet(),
            sourceRevision = snapshot.revision,
        )
    }

    private fun List<FeatureMatch>.isCompact(maximumSpan: Int): Boolean {
        val blocks = flatMap(FeatureMatch::blocks).distinct()
        val minX = blocks.minOfOrNull(StructureBlockSnapshot::x) ?: return false
        val maxX = blocks.maxOf(StructureBlockSnapshot::x)
        val minZ = blocks.minOfOrNull(StructureBlockSnapshot::z) ?: return false
        val maxZ = blocks.maxOf(StructureBlockSnapshot::z)
        return maxX - minX <= maximumSpan && maxZ - minZ <= maximumSpan
    }

    private fun List<StructureBlockSnapshot>.anchorChunk(): ChunkCoordinate {
        val x = minOf(StructureBlockSnapshot::x)
        val z = minOf(StructureBlockSnapshot::z)
        return ChunkCoordinate(Math.floorDiv(x, CHUNK_SIDE), Math.floorDiv(z, CHUNK_SIDE))
    }

    private data class SignatureRule(
        val type: StructureType,
        val features: List<FeatureRequirement>,
        val maximumHorizontalSpan: Int,
        val geometry: (Map<String, FeatureMatch>) -> Boolean = { true },
    )

    private data class FeatureRequirement(
        val key: String,
        val minimumCount: Int,
        val blockIds: Set<String>,
    ) {
        fun match(blocks: List<StructureBlockSnapshot>): FeatureMatch {
            val matches = blocks.filter { it.blockId in blockIds }.distinctBy { block ->
                Triple(block.x, block.y, block.z)
            }
            return FeatureMatch(key, matches.takeIf { it.size >= minimumCount }.orEmpty())
        }
    }

    private data class FeatureMatch(
        val key: String,
        val blocks: List<StructureBlockSnapshot>,
    )

    private fun Map<String, FeatureMatch>.nearby(first: String, second: String, maximumDistance: Int): Boolean {
        val firstBlocks = get(first)?.blocks.orEmpty()
        val secondBlocks = get(second)?.blocks.orEmpty()
        return firstBlocks.any { left ->
            secondBlocks.any { right ->
                kotlin.math.abs(left.x - right.x) <= maximumDistance &&
                    kotlin.math.abs(left.z - right.z) <= maximumDistance
            }
        }
    }

    private val rules = listOf(
        SignatureRule(
            type = StructureType.IGLOO,
            features = listOf(
                feature("snow_shell", 1, "snow_block"),
                feature("basement_redstone", 1, "redstone_torch"),
                feature("basement_loot", 1, "chest"),
                feature("ladder", 2, "ladder"),
            ),
            maximumHorizontalSpan = 8,
            geometry = { it.nearby("basement_redstone", "basement_loot", 2) },
        ),
        SignatureRule(
            type = StructureType.DESERT_PYRAMID,
            features = listOf(
                feature("treasure_pattern", 4, "blue_terracotta"),
                feature("treasure_trigger", 1, "stone_pressure_plate"),
                feature("treasure_chests", 2, "chest"),
            ),
            maximumHorizontalSpan = 10,
            geometry = { it.nearby("treasure_pattern", "treasure_trigger", 1) },
        ),
        SignatureRule(
            type = StructureType.JUNGLE_TEMPLE,
            features = listOf(
                feature("tripwire_pair", 2, "tripwire_hook"),
                feature("redstone_trap", 1, "redstone_wire"),
                feature("trap_dispenser", 1, "dispenser"),
                feature("temple_stonework", 2, "mossy_cobblestone", "cobblestone"),
            ),
            maximumHorizontalSpan = 12,
            geometry = { it.nearby("tripwire_pair", "redstone_trap", 4) },
        ),
        SignatureRule(
            type = StructureType.SWAMP_HUT,
            features = listOf(
                feature("cauldron", 1, "cauldron"),
                feature("crafting", 1, "crafting_table"),
                feature("fence_deck", 3, "oak_fence"),
                feature("spruce_floor", 4, "spruce_planks"),
            ),
            maximumHorizontalSpan = 12,
            geometry = { it.nearby("cauldron", "crafting", 3) },
        ),
        SignatureRule(
            type = StructureType.SHIPWRECK,
            features = listOf(
                feature("ship_loot", 2, "chest"),
                feature("hull_planks", 4, "oak_planks"),
                feature("deck_detail", 1, "oak_stairs", "oak_trapdoor"),
                feature("hull_log", 1, "stripped_oak_log"),
            ),
            maximumHorizontalSpan = 14,
            geometry = { it.nearby("ship_loot", "hull_planks", 5) },
        ),
        SignatureRule(
            type = StructureType.PILLAGER_OUTPOST,
            features = listOf(
                feature("tower_logs", 4, "dark_oak_log"),
                feature("tower_planks", 4, "dark_oak_planks"),
                feature("tower_stone", 4, "cobblestone"),
                feature("tower_fence", 4, "oak_fence"),
            ),
            maximumHorizontalSpan = 14,
        ),
        SignatureRule(
            type = StructureType.OCEAN_MONUMENT,
            features = listOf(
                feature("prismarine", 4, "prismarine"),
                feature("prismarine_bricks", 4, "prismarine_bricks"),
                feature("dark_prismarine", 4, "dark_prismarine"),
                feature("sea_lanterns", 2, "sea_lantern"),
            ),
            maximumHorizontalSpan = 14,
        ),
    )

    private fun feature(key: String, minimumCount: Int, vararg blockIds: String) = FeatureRequirement(
        key = key,
        minimumCount = minimumCount,
        blockIds = blockIds.mapTo(linkedSetOf(), String::toStableBlockPath),
    )

    private const val OVERWORLD_DIMENSION = "minecraft:overworld"
    private const val CHUNK_SIDE = 16
    private const val MINIMUM_FEATURES_FOR_AMBIGUOUS_EVIDENCE = 2
}

internal fun String.toStableBlockPath(): String {
    val normalized = trim().substringBefore('[').lowercase()
    if (':' !in normalized) return normalized

    val namespace = normalized.substringBefore(':')
    val path = normalized.substringAfter(':')
    return if (namespace == "minecraft") path else "$namespace:$path"
}

private fun Iterable<String>.immutableSortedSet(): Set<String> = Collections.unmodifiableSet(toSortedSet())
