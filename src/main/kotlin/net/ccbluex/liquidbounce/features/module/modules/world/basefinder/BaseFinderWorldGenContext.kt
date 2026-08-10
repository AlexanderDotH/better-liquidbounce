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

import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.biome.TheEndBiomeSource
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets
import net.minecraft.world.level.levelgen.structure.StructureSet

/**
 * Reusable vanilla generator handle for a configured world seed and dimension.
 *
 * Built from a [RegistryAccess] that includes worldgen registries (see [BaseFinderVanillaWorldGenAccess]).
 * Custom server generators are out of scope for v1.
 */
internal class BaseFinderWorldGenContext private constructor(
    val seed: Long,
    val dimensionKey: String,
    val generator: ChunkGenerator,
    val randomState: RandomState,
    val heightAccessor: LevelHeightAccessor,
    val structureState: ChunkGeneratorStructureState,
) {
    fun confirmedStructureFalsePositives(chunk: ChunkCoordinate): Set<BaseFalsePositive> = buildSet {
        if (hasStructureSet(BuiltinStructureSets.VILLAGES, chunk)) add(BaseFalsePositive.VILLAGE)
        if (hasStructureSet(BuiltinStructureSets.MINESHAFTS, chunk) ||
            hasStructureSet(BuiltinStructureSets.TRAIL_RUINS, chunk) ||
            hasStructureSet(BuiltinStructureSets.ANCIENT_CITIES, chunk) ||
            hasStructureSet(BuiltinStructureSets.TRIAL_CHAMBERS, chunk)
        ) {
            add(BaseFalsePositive.MINESHAFT_OR_DUNGEON)
        }
        if (hasStructureSet(BuiltinStructureSets.RUINED_PORTALS, chunk)) {
            add(BaseFalsePositive.RUINED_PORTAL)
        }
        if (hasStructureSet(BuiltinStructureSets.NETHER_COMPLEXES, chunk) ||
            hasStructureSet(BuiltinStructureSets.END_CITIES, chunk)
        ) {
            add(BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY)
        }
        if (hasAnyStructureSet(LOOT_CONTAINER_STRUCTURE_SETS, chunk)) {
            add(BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER)
        }
    }

    private fun hasStructureSet(
        key: ResourceKey<StructureSet>,
        chunk: ChunkCoordinate,
    ): Boolean {
        val holder = structureState.possibleStructureSets().firstOrNull { it.matchesKey(key) } ?: return false
        return structureState.hasStructureChunkInRange(holder, chunk.x, chunk.z, STRUCTURE_RANGE_CHUNKS)
    }

    private fun hasAnyStructureSet(
        keys: Collection<ResourceKey<StructureSet>>,
        chunk: ChunkCoordinate,
    ): Boolean = keys.any { hasStructureSet(it, chunk) }

    companion object {
        private const val OVERWORLD = "minecraft:overworld"
        private const val NETHER = "minecraft:the_nether"
        private const val END = "minecraft:the_end"
        private const val STRUCTURE_RANGE_CHUNKS = 0
        private val LOOT_CONTAINER_STRUCTURE_SETS = listOf(
            BuiltinStructureSets.DESERT_PYRAMIDS,
            BuiltinStructureSets.JUNGLE_TEMPLES,
            BuiltinStructureSets.IGLOOS,
            BuiltinStructureSets.SHIPWRECKS,
            BuiltinStructureSets.OCEAN_RUINS,
            BuiltinStructureSets.BURIED_TREASURES,
        )

        fun createOrNull(
            seed: Long,
            dimensionKey: String,
            registryAccess: RegistryAccess,
            heightAccessor: LevelHeightAccessor,
        ): BaseFinderWorldGenContext? = create(seed, dimensionKey, registryAccess, heightAccessor).getOrNull()

        fun create(
            seed: Long,
            dimensionKey: String,
            registryAccess: RegistryAccess,
            heightAccessor: LevelHeightAccessor,
        ): Result<BaseFinderWorldGenContext> = runCatching {
            val noiseSettingsKey = noiseSettingsKey(dimensionKey)
                ?: error("Seed compare unsupported dimension $dimensionKey")
            val noiseSettingsLookup: HolderLookup.RegistryLookup<NoiseGeneratorSettings> =
                registryAccess.lookup(Registries.NOISE_SETTINGS).orElseThrow {
                    IllegalStateException("Missing registry ${Registries.NOISE_SETTINGS}")
                }
            val structureSets: HolderLookup.RegistryLookup<StructureSet> =
                registryAccess.lookup(Registries.STRUCTURE_SET).orElseThrow {
                    IllegalStateException("Missing registry ${Registries.STRUCTURE_SET}")
                }
            val biomeSource = biomeSource(dimensionKey, registryAccess)
            val noiseSettings = noiseSettingsLookup.getOrThrow(noiseSettingsKey)
            val generator = NoiseBasedChunkGenerator(biomeSource, noiseSettings)
            val randomState = RandomState.create(registryAccess, noiseSettingsKey, seed)
            val structureState = ChunkGeneratorStructureState.createForNormal(
                randomState,
                seed,
                biomeSource,
                structureSets,
            )
            structureState.ensureStructuresGenerated()
            BaseFinderWorldGenContext(
                seed = seed,
                dimensionKey = dimensionKey,
                generator = generator,
                randomState = randomState,
                heightAccessor = heightAccessor,
                structureState = structureState,
            )
        }

        private fun noiseSettingsKey(dimensionKey: String): ResourceKey<NoiseGeneratorSettings>? =
            when (dimensionKey) {
                OVERWORLD -> NoiseGeneratorSettings.OVERWORLD
                NETHER -> NoiseGeneratorSettings.NETHER
                END -> NoiseGeneratorSettings.END
                else -> null
            }

        private fun biomeSource(dimensionKey: String, registryAccess: RegistryAccess): BiomeSource =
            when (dimensionKey) {
                OVERWORLD -> {
                    val biomeParameterLookup =
                        registryAccess.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).orElseThrow {
                            IllegalStateException(
                                "Missing registry ${Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST}",
                            )
                        }
                    val biomeParameters =
                        biomeParameterLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
                    MultiNoiseBiomeSource.createFromPreset(biomeParameters)
                }
                NETHER -> {
                    val biomeParameterLookup =
                        registryAccess.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).orElseThrow {
                            IllegalStateException(
                                "Missing registry ${Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST}",
                            )
                        }
                    val biomeParameters =
                        biomeParameterLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)
                    MultiNoiseBiomeSource.createFromPreset(biomeParameters)
                }
                END -> {
                    val biomes: HolderLookup.RegistryLookup<Biome> =
                        registryAccess.lookup(Registries.BIOME).orElseThrow {
                            IllegalStateException("Missing registry ${Registries.BIOME}")
                        }
                    TheEndBiomeSource.create(biomes)
                }
                else -> error("Seed compare unsupported dimension $dimensionKey")
            }
    }
}

private fun Holder<StructureSet>.matchesKey(key: ResourceKey<StructureSet>): Boolean =
    unwrapKey().map { it == key }.orElse(false)
