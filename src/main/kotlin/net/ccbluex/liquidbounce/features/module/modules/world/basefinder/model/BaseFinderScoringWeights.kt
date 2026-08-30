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

/** Immutable, ordinal-indexed snapshot of the complete BaseFinder score matrix. */
internal class BaseFinderScoringWeights private constructor(source: IntArray) {

    private val values = source.copyOf()

    init {
        require(values.size == BaseFinderScoreWeight.entries.size) { "Incomplete BaseFinder scoring matrix" }
    }

    operator fun get(weight: BaseFinderScoreWeight): Int = values[weight.ordinal]

    fun with(weight: BaseFinderScoreWeight, value: Int): BaseFinderScoringWeights {
        val clampedValue = value.coerceIn(weight.range)
        if (values[weight.ordinal] == clampedValue) return this
        return BaseFinderScoringWeights(values.copyOf().also { copy ->
            copy[weight.ordinal] = clampedValue
        })
    }

    fun toPersistedMap(): Map<String, Int> = BaseFinderScoreWeight.entries.associate { weight ->
        weight.persistedKey to values[weight.ordinal]
    }

    override fun equals(other: Any?): Boolean = other is BaseFinderScoringWeights && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "BaseFinderScoringWeights(${toPersistedMap()})"

    companion object {
        val DEFAULT = BaseFinderScoringWeights(
            BaseFinderScoreWeight.entries.map(BaseFinderScoreWeight::defaultValue).toIntArray(),
        )

        fun fromPersistedMap(persistedValues: Map<String, Int>): BaseFinderScoringWeights {
            var result = DEFAULT
            persistedValues.forEach { (persistedKey, value) ->
                val weight = BaseFinderScoreWeight.byPersistedKey[persistedKey] ?: return@forEach
                result = result.with(weight, value)
            }
            return result
        }
    }
}

internal val BaseFalsePositive.scoreWeight: BaseFinderScoreWeight
    get() = when (this) {
        BaseFalsePositive.VILLAGE -> BaseFinderScoreWeight.FALSE_POSITIVE_VILLAGE
        BaseFalsePositive.MINESHAFT_OR_DUNGEON -> BaseFinderScoreWeight.FALSE_POSITIVE_MINESHAFT_OR_DUNGEON
        BaseFalsePositive.RUINED_PORTAL -> BaseFinderScoreWeight.FALSE_POSITIVE_RUINED_PORTAL
        BaseFalsePositive.FORTRESS_BASTION_OR_END_CITY ->
            BaseFinderScoreWeight.FALSE_POSITIVE_FORTRESS_BASTION_OR_END_CITY
        BaseFalsePositive.ISOLATED_GENERATED_LOOT_CONTAINER ->
            BaseFinderScoreWeight.FALSE_POSITIVE_ISOLATED_GENERATED_LOOT_CONTAINER
        BaseFalsePositive.HOMOGENEOUS_SIGNAL -> BaseFinderScoreWeight.FALSE_POSITIVE_HOMOGENEOUS_SIGNAL
    }
