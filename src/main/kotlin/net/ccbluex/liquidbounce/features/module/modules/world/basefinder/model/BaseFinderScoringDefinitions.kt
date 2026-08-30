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

private val SCORE_RANGE = 0..100
private val COUNT_RANGE = 0..10

/** Stable ClickGUI groups for BaseFinder's configurable scoring matrix. */
internal enum class BaseFinderScoreGroup(val settingName: String) {
    STORAGE("Storage"),
    UTILITIES("Utilities"),
    AUTOMATION("Automation"),
    ENTITIES("Entities"),
    STRUCTURAL("Structural"),
    GEOMETRY("Geometry"),
    SEED_MISMATCH("SeedMismatch"),
    ACTIVITY("Activity"),
    CHUNK_TRAILS("ChunkTrails"),
    BONUSES("Bonuses"),
    FALSE_POSITIVES("FalsePositives"),
    POLICY("Policy"),
}

/**
 * Every adjustable integer which participates in BaseFinder's final score.
 *
 * [persistedKey] is an on-disk compatibility contract. Enum names and ClickGUI labels may be improved later,
 * while these keys must remain stable so a per-server profile keeps its meaning across releases.
 */
@Suppress("MagicNumber")
internal enum class BaseFinderScoreWeight(
    val persistedKey: String,
    val defaultValue: Int,
    val range: IntRange,
    val group: BaseFinderScoreGroup,
    val settingName: String,
) {
    STORAGE_STANDARD_CONTAINER("storage.standard_container", 3, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "StandardContainer"),
    STORAGE_HIGH_VALUE_CONTAINER("storage.high_value_container", 4, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "HighValueContainer"),
    STORAGE_UTILITY_CONTAINER("storage.utility_container", 1, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "UtilityContainer"),
    STORAGE_CONTAINER_MINECART("storage.container_minecart", 3, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "ContainerMinecart"),
    STORAGE_FURNACE_MINECART("storage.furnace_minecart", 1, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "FurnaceMinecart"),
    STORAGE_CONTAINER_VEHICLE("storage.container_vehicle", 3, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "ContainerVehicle"),
    STORAGE_LOG_MULTIPLIER("storage.log_multiplier", 6, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "LogMultiplier"),
    STORAGE_FAMILY_MAXIMUM("storage.family_maximum", 30, SCORE_RANGE, BaseFinderScoreGroup.STORAGE,
        "FamilyMaximum"),

    UTILITY_CATEGORY("utilities.category", 3, SCORE_RANGE, BaseFinderScoreGroup.UTILITIES, "Category"),
    UTILITIES_FAMILY_MAXIMUM("utilities.family_maximum", 18, SCORE_RANGE, BaseFinderScoreGroup.UTILITIES,
        "FamilyMaximum"),

    AUTOMATION_DIVERSITY("automation.diversity", 8, SCORE_RANGE, BaseFinderScoreGroup.AUTOMATION, "Diversity"),
    AUTOMATION_DENSITY("automation.density", 8, SCORE_RANGE, BaseFinderScoreGroup.AUTOMATION, "Density"),
    AUTOMATION_ORGANIZED_PATTERN("automation.organized_pattern", 4, SCORE_RANGE,
        BaseFinderScoreGroup.AUTOMATION, "OrganizedPattern"),
    AUTOMATION_FAMILY_MAXIMUM("automation.family_maximum", 20, SCORE_RANGE, BaseFinderScoreGroup.AUTOMATION,
        "FamilyMaximum"),

    ENTITY_DIVERSITY("entities.diversity", 6, SCORE_RANGE, BaseFinderScoreGroup.ENTITIES, "Diversity"),
    ENTITY_DENSITY("entities.density", 4, SCORE_RANGE, BaseFinderScoreGroup.ENTITIES, "Density"),
    ENTITY_CONTAINER_VEHICLE("entities.container_vehicle", 2, SCORE_RANGE, BaseFinderScoreGroup.ENTITIES,
        "ContainerVehicle"),
    ENTITY_MINECART_STASH("entities.minecart_stash", 5, SCORE_RANGE, BaseFinderScoreGroup.ENTITIES,
        "MinecartStash"),
    ENTITIES_FAMILY_MAXIMUM("entities.family_maximum", 12, SCORE_RANGE, BaseFinderScoreGroup.ENTITIES,
        "FamilyMaximum"),

    STRUCTURAL_PORTAL("structural.portal", 5, SCORE_RANGE, BaseFinderScoreGroup.STRUCTURAL, "Portal"),
    STRUCTURAL_USABLE_BED("structural.usable_bed", 3, SCORE_RANGE, BaseFinderScoreGroup.STRUCTURAL, "UsableBed"),
    STRUCTURAL_INFRASTRUCTURE("structural.infrastructure", 4, SCORE_RANGE, BaseFinderScoreGroup.STRUCTURAL,
        "Infrastructure"),
    STRUCTURAL_DECORATION_CLUSTER("structural.decoration_cluster", 2, SCORE_RANGE,
        BaseFinderScoreGroup.STRUCTURAL, "DecorationCluster"),
    STRUCTURAL_FAMILY_MAXIMUM("structural.family_maximum", 12, SCORE_RANGE, BaseFinderScoreGroup.STRUCTURAL,
        "FamilyMaximum"),

    GEOMETRY_CAVE_DISTURBANCE("geometry.cave_disturbance", 5, SCORE_RANGE, BaseFinderScoreGroup.GEOMETRY,
        "CaveDisturbance"),
    GEOMETRY_ARTIFICIAL_PATTERN("geometry.artificial_pattern", 5, SCORE_RANGE, BaseFinderScoreGroup.GEOMETRY,
        "ArtificialPattern"),
    GEOMETRY_FAMILY_MAXIMUM("geometry.family_maximum", 10, SCORE_RANGE, BaseFinderScoreGroup.GEOMETRY,
        "FamilyMaximum"),

    SEED_UNEXPECTED_0_TO_3("seed.unexpected.0_to_3", 0, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected0To3"),
    SEED_UNEXPECTED_4_TO_7("seed.unexpected.4_to_7", 8, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected4To7"),
    SEED_UNEXPECTED_8_TO_15("seed.unexpected.8_to_15", 16, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected8To15"),
    SEED_UNEXPECTED_16_TO_31("seed.unexpected.16_to_31", 24, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected16To31"),
    SEED_UNEXPECTED_32_TO_63("seed.unexpected.32_to_63", 32, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected32To63"),
    SEED_UNEXPECTED_64_PLUS("seed.unexpected.64_plus", 40, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Unexpected64Plus"),
    SEED_MISSING_0_TO_7("seed.missing.0_to_7", 0, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing0To7"),
    SEED_MISSING_8_TO_15("seed.missing.8_to_15", 5, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing8To15"),
    SEED_MISSING_16_TO_31("seed.missing.16_to_31", 10, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing16To31"),
    SEED_MISSING_32_TO_63("seed.missing.32_to_63", 15, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing32To63"),
    SEED_MISSING_64_TO_127("seed.missing.64_to_127", 20, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing64To127"),
    SEED_MISSING_128_PLUS("seed.missing.128_plus", 25, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "Missing128Plus"),
    SEED_FEATURES_MAXIMUM("seed.features_maximum", 65, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "FeaturesMaximum"),
    SEED_BASE_COLUMN_MAXIMUM("seed.base_column_maximum", 20, SCORE_RANGE, BaseFinderScoreGroup.SEED_MISMATCH,
        "BaseColumnMaximum"),

    ACTIVITY_CATEGORY("activity.category", 2, SCORE_RANGE, BaseFinderScoreGroup.ACTIVITY, "Category"),
    ACTIVITY_FAMILY_MAXIMUM("activity.family_maximum", 6, SCORE_RANGE, BaseFinderScoreGroup.ACTIVITY,
        "FamilyMaximum"),
    CHUNK_TRAILS_BOUNDARY("chunk_trails.boundary", 2, SCORE_RANGE, BaseFinderScoreGroup.CHUNK_TRAILS,
        "Boundary"),
    CHUNK_TRAILS_ENDPOINT("chunk_trails.endpoint", 2, SCORE_RANGE, BaseFinderScoreGroup.CHUNK_TRAILS,
        "Endpoint"),
    CHUNK_TRAILS_FAMILY_MAXIMUM("chunk_trails.family_maximum", 4, SCORE_RANGE,
        BaseFinderScoreGroup.CHUNK_TRAILS, "FamilyMaximum"),

    COMPACT_INHABITED_BASE("bonuses.compact_inhabited_base", 32, SCORE_RANGE, BaseFinderScoreGroup.BONUSES,
        "CompactInhabitedBase"),
    DIVERSITY_THREE_FAMILIES("bonuses.diversity_three_families", 4, SCORE_RANGE, BaseFinderScoreGroup.BONUSES,
        "DiversityThreeFamilies"),
    DIVERSITY_FOUR_PLUS_FAMILIES("bonuses.diversity_four_plus_families", 8, SCORE_RANGE,
        BaseFinderScoreGroup.BONUSES, "DiversityFourPlusFamilies"),

    FALSE_POSITIVE_VILLAGE("false_positive.village", 30, SCORE_RANGE, BaseFinderScoreGroup.FALSE_POSITIVES,
        "Village"),
    FALSE_POSITIVE_MINESHAFT_OR_DUNGEON("false_positive.mineshaft_or_dungeon", 25, SCORE_RANGE,
        BaseFinderScoreGroup.FALSE_POSITIVES, "MineshaftOrDungeon"),
    FALSE_POSITIVE_RUINED_PORTAL("false_positive.ruined_portal", 20, SCORE_RANGE,
        BaseFinderScoreGroup.FALSE_POSITIVES, "RuinedPortal"),
    FALSE_POSITIVE_FORTRESS_BASTION_OR_END_CITY("false_positive.fortress_bastion_or_end_city", 25,
        SCORE_RANGE, BaseFinderScoreGroup.FALSE_POSITIVES, "FortressBastionOrEndCity"),
    FALSE_POSITIVE_ISOLATED_GENERATED_LOOT_CONTAINER("false_positive.isolated_generated_loot_container", 20,
        SCORE_RANGE, BaseFinderScoreGroup.FALSE_POSITIVES, "IsolatedGeneratedLootContainer"),
    FALSE_POSITIVE_HOMOGENEOUS_SIGNAL("false_positive.homogeneous_signal", 15, SCORE_RANGE,
        BaseFinderScoreGroup.FALSE_POSITIVES, "HomogeneousSignal"),
    FALSE_POSITIVE_PENALTY_MAXIMUM("false_positive.penalty_maximum", 50, SCORE_RANGE,
        BaseFinderScoreGroup.FALSE_POSITIVES, "PenaltyMaximum"),

    SEED_ONLY_CONFIDENCE_CAP("policy.seed_only_confidence_cap", 89, SCORE_RANGE, BaseFinderScoreGroup.POLICY,
        "SeedOnlyConfidenceCap"),
    CORROBORATION_FAMILY_MINIMUM("policy.corroboration_family_minimum", 5, SCORE_RANGE,
        BaseFinderScoreGroup.POLICY, "CorroborationFamilyMinimum"),
    CORROBORATION_FAMILY_COUNT("policy.corroboration_family_count", 2, COUNT_RANGE,
        BaseFinderScoreGroup.POLICY, "CorroborationFamilyCount"),
    CORROBORATION_STRONG_FAMILY("policy.corroboration_strong_family", 20, SCORE_RANGE,
        BaseFinderScoreGroup.POLICY, "CorroborationStrongFamily"),
    STANDALONE_POST_PENALTY_MINIMUM("policy.standalone_post_penalty_minimum", 35, SCORE_RANGE,
        BaseFinderScoreGroup.POLICY, "StandalonePostPenaltyMinimum"),
    MINECART_SEED_CORROBORATION_MINIMUM("policy.minecart_seed_corroboration_minimum", 8, SCORE_RANGE,
        BaseFinderScoreGroup.POLICY, "MinecartSeedCorroborationMinimum"),
    LEGACY_STORAGE_ACCEPTANCE_MINIMUM("policy.legacy_storage_acceptance_minimum", 24, SCORE_RANGE,
        BaseFinderScoreGroup.POLICY, "LegacyStorageAcceptanceMinimum"),
    ;

    init {
        require(defaultValue in range) { "$name default must be inside its configurable range" }
        require(persistedKey.isNotBlank()) { "$name must have a stable persistence key" }
        require(settingName.isNotBlank()) { "$name must have a ClickGUI setting name" }
    }

    internal companion object {
        val byPersistedKey: Map<String, BaseFinderScoreWeight> = entries.associateBy { weight ->
            weight.persistedKey
        }.also { weightsByKey ->
            check(weightsByKey.size == entries.size) { "BaseFinder scoring persistence keys must be unique" }
        }
    }
}
