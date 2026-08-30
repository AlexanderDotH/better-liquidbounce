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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceStrategy
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlockRotation
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

internal data class PreparedAction(
    val action: LitematicaPrintAction,
    val rotation: Rotation,
    val hitResult: BlockHitResult?,
    val interaction: LitematicaCellInteractionSnapshot?,
    val strategy: LitematicaEasyPlaceStrategy?,
)

internal class LitematicaActionPreparer : MinecraftShortcuts {
    fun prepare(
        action: LitematicaPrintAction,
        interaction: LitematicaCellInteractionSnapshot?,
        range: Double,
    ): PreparedAction? = when (action.kind) {
        LitematicaActionKind.BREAK -> prepareBreak(action, range)
        LitematicaActionKind.FLUID_PICKUP, LitematicaActionKind.FLUID_PLACE -> prepareFluid(action, interaction)
        LitematicaActionKind.PLACE, LitematicaActionKind.AIR_PLACE -> preparePlacement(action, interaction)
    }

    private fun prepareBreak(action: LitematicaPrintAction, range: Double): PreparedAction? {
        val position = action.target.toBlockPos()
        val state = world.getBlockState(position)
        val target = raytraceBlockRotation(player.eyePosition, position, state, range, range) ?: return null
        val hit = raytraceBlock(range + 1.0, target.rotation, position, state) ?: return null
        return PreparedAction(action, target.rotation, hit, null, null)
    }

    private fun prepareFluid(
        action: LitematicaPrintAction,
        interaction: LitematicaCellInteractionSnapshot?,
    ): PreparedAction? {
        val position = action.target.toBlockPos()
        if (action.kind == LitematicaActionKind.FLUID_PLACE) return prepareFluidPlacement(action, interaction)
        val point = Vec3.atCenterOf(position)
        return PreparedAction(
            action,
            Rotation.lookingAt(point, player.eyePosition),
            BlockHitResult(point, Direction.UP, position, false),
            null,
            null,
        )
    }

    private fun prepareFluidPlacement(
        action: LitematicaPrintAction,
        interaction: LitematicaCellInteractionSnapshot?,
    ): PreparedAction? {
        val neighbor = interaction?.neighborHitResult ?: return null
        return PreparedAction(
            action,
            Rotation.lookingAt(interaction.rotationTarget, player.eyePosition),
            neighbor,
            interaction,
            LitematicaEasyPlaceStrategy.NEIGHBOR,
        )
    }

    private fun preparePlacement(
        action: LitematicaPrintAction,
        interaction: LitematicaCellInteractionSnapshot?,
    ): PreparedAction? {
        interaction ?: return null
        if (action.kind != LitematicaActionKind.AIR_PLACE && interaction.neighborHitResult == null) return null
        val strategy = interaction.neighborHitResult?.let { LitematicaEasyPlaceStrategy.NEIGHBOR }
            ?: LitematicaEasyPlaceStrategy.DIRECT_AIR_PLACE
        return PreparedAction(
            action,
            Rotation.lookingAt(interaction.rotationTarget, player.eyePosition),
            interaction.neighborHitResult,
            interaction,
            strategy,
        )
    }
}
