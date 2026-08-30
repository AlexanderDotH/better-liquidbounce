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

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup

internal object BaseFinderEvidenceSettings : ValueGroup("Evidence") {
    internal val storage by boolean("Storage", true)
    internal val utilities by boolean("Utilities", true)
    internal val automation by boolean("Automation", true)
    internal val entities by boolean("Entities", true)
    internal val structural by boolean("Structural", true)
    internal val geometry by boolean("Geometry", true)
    internal val activity by boolean("Activity", true)
    internal val chunkTrails by boolean("ChunkTrails", true)
}

internal object BaseFinderSeedMismatchSettings : ToggleableValueGroup(
    ModuleBaseFinder,
    "SeedMismatch",
    true,
    aliases = listOf("SeedCompare"),
) {
    private val worldSeedSetting = text("WorldSeed", "").onChanged {
        ModuleBaseFinder.onServerScopedSettingsChanged()
    }
    internal val worldSeed by worldSeedSetting

    internal fun applyWorldSeed(worldSeed: String) {
        worldSeedSetting.set(worldSeed)
    }

    /**
     * Features = full noise→carvers→biome decoration from the typed seed (background server; SP+MP).
     * Base column = fast noise-column API only.
     */
    internal val backend by enumChoice("Backend", BaseFinderWorldBackend.FEATURES)
    /**
     * Max Chebyshev radius (chunks) for SeedMismatch outlines around the player.
     * Effective radius is also capped by the client's render distance so we only scan loaded terrain.
     */
    internal val scanChunks by int("ScanChunks", 12, 1..16, "chunks")

    /**
     * Also outline solid blocks whose material differs from the seed (cobblestone where stone is
     * expected) instead of only missing/extra blocks. Materials a ticked world converts between
     * (grass↔path, water↔ice, …) stay silent. Overlay only — never changes confidence.
     *
     * Requires [BaseFinderWorldBackend.FEATURES]; the base-column backend has no real materials.
     */
    internal val compareMaterials by boolean("CompareMaterials", false)
}

internal object BaseFinderScoringSettings : ValueGroup("Scoring") {
    private val settingsByWeight: Map<BaseFinderScoreWeight, Value<Int>>

    init {
        val mutableSettings = linkedMapOf<BaseFinderScoreWeight, Value<Int>>()
        BaseFinderScoreGroup.entries.forEach { group ->
            val section = ValueGroup(group.settingName)
            BaseFinderScoreWeight.entries
                .filter { weight -> weight.group == group }
                .forEach { weight ->
                    mutableSettings[weight] = section.int(
                        name = weight.settingName,
                        default = weight.defaultValue,
                        range = weight.range,
                    ).onChanged {
                        ModuleBaseFinder.onServerScopedSettingsChanged()
                    }
                }
            tree(section)
        }
        settingsByWeight = mutableSettings
        // Interop submits the complete group in order; reset must run after the submitted sliders.
        action("ResetToDefaults") { resetToDefaults() }
    }

    internal fun snapshot(): BaseFinderScoringWeights = BaseFinderScoringWeights.fromPersistedMap(
        settingsByWeight.mapKeys { (weight, _) -> weight.persistedKey }
            .mapValues { (_, setting) -> setting.get() },
    )

    internal fun applyWeights(weights: BaseFinderScoringWeights) {
        settingsByWeight.forEach { (weight, setting) ->
            setting.set(weights[weight])
        }
    }

    internal fun resetToDefaults() {
        ModuleBaseFinder.updateServerScopedSettingsAtomically {
            applyWeights(BaseFinderScoringWeights.DEFAULT)
        }
    }
}

internal object BaseFinderAlertSettings : ValueGroup("Alerts") {
    internal val notifications by boolean("Notifications", true)
    internal val chatCoordinates by boolean("ChatCoordinates", true)
}
