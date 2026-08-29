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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState

internal object Litematica262BlockSnapshotMapper {

    fun snapshot(
        state: BlockState,
        hasBlockEntity: Boolean = state.hasBlockEntity(),
        reproducible: Boolean = true,
    ): LitematicaBlockSnapshot {
        if (state.isAir) return LitematicaBlockSnapshot.air()

        val fluid = state.fluidState
        val kind = when {
            state.liquid() && fluid.isSource -> LitematicaBlockKind.FLUID_SOURCE
            state.liquid() && !fluid.isEmpty -> LitematicaBlockKind.FLUID_FLOWING
            else -> LitematicaBlockKind.SOLID
        }
        return LitematicaBlockSnapshot(
            id = BuiltInRegistries.BLOCK.getKey(state.block).toString(),
            properties = state.values.toList().associate { value ->
                value.property().name to value.valueName()
            },
            kind = kind,
            replaceable = state.canBeReplaced(),
            hasBlockEntity = hasBlockEntity,
            reproducible = reproducible && kind != LitematicaBlockKind.FLUID_FLOWING,
        )
    }

    fun isSupportCandidate(state: BlockState): Boolean = !state.canBeReplaced()
}
