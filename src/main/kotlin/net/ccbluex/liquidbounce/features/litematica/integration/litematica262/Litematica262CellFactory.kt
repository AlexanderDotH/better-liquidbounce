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

import fi.dy.masa.litematica.materials.MaterialCache
import fi.dy.masa.litematica.world.WorldSchematic
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementMethod
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.block.config.BlockOffsetOptions
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.features.block.config.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.features.block.planner.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.features.block.config.FaceHandlingOptions
import net.ccbluex.liquidbounce.features.block.config.PlayerLocationOnPlacement
import net.ccbluex.liquidbounce.features.block.planner.findBestBlockPlacementTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

internal data class Litematica262ScannedCell(
    val snapshot: LitematicaCellSnapshot,
    val interaction: LitematicaCellInteractionSnapshot?,
)

internal class Litematica262CellFactory {
    fun create(
        view: Litematica262PlacementView,
        position: BlockPos,
        desiredCell: Litematica262PlacementView.DesiredCell,
        world: ClientLevel,
        schematicWorld: WorldSchematic,
    ): Litematica262ScannedCell {
        val actualState = world.getBlockState(position)
        val desired = Litematica262BlockSnapshotMapper.snapshot(desiredCell.state, desiredCell.reproducible)
        val actual = Litematica262BlockSnapshotMapper.snapshot(
            actualState, actualState.hasBlockEntity() || world.getBlockEntity(position) != null,
        )
        val required = requiredItem(desired.kind, desiredCell.state, schematicWorld, position)
        val materialId = required.takeUnless(ItemStack::isEmpty)?.let {
            BuiltInRegistries.ITEM.getKey(it.item).toString()
        }
        val available = desired.kind == LitematicaBlockKind.AIR ||
            !required.isEmpty && Litematica262Inventory.availableCount(required) > 0
        val target = placementTarget(position, required, desired.kind, actual.replaceable)
        val method = placementMethod(desired.kind, actual.replaceable, required, target)
        val domainPosition = position.toDomainPosition()
        val snapshot = LitematicaCellSnapshot(
            domainPosition, desired, actual, method, available,
            if (available) materialId else materialId ?: desired.id,
        )
        return Litematica262ScannedCell(snapshot, interaction(view, domainPosition, position, desired.kind, method, target))
    }

    private fun placementMethod(
        kind: LitematicaBlockKind,
        replaceable: Boolean,
        required: ItemStack,
        target: BlockPlacementTarget?,
    ): LitematicaPlacementMethod = when {
        kind != LitematicaBlockKind.SOLID || !replaceable -> LitematicaPlacementMethod.UNAVAILABLE
        required.isEmpty -> LitematicaPlacementMethod.UNAVAILABLE
        target != null -> LitematicaPlacementMethod.NEIGHBOR_FACE
        else -> LitematicaPlacementMethod.AIR_PLACE
    }

    private fun interaction(
        view: Litematica262PlacementView,
        domainPosition: LitematicaPosition,
        position: BlockPos,
        kind: LitematicaBlockKind,
        method: LitematicaPlacementMethod,
        target: BlockPlacementTarget?,
    ): LitematicaCellInteractionSnapshot? {
        val available = method != LitematicaPlacementMethod.UNAVAILABLE ||
            kind == LitematicaBlockKind.FLUID_SOURCE && target != null
        if (!available) return null
        return LitematicaCellInteractionSnapshot(
            view.metadata.id,
            domainPosition,
            target?.blockHitResult,
            target?.interactionPoint ?: Vec3.atCenterOf(position),
        )
    }

    private fun placementTarget(
        position: BlockPos,
        required: ItemStack,
        kind: LitematicaBlockKind,
        replaceable: Boolean,
    ): BlockPlacementTarget? {
        if (required.isEmpty || kind !in PLACEABLE_KINDS || !replaceable) return null
        val player = Minecraft.getInstance().player ?: return null
        val options = BlockPlacementTargetFindingOptions(
            BlockOffsetOptions.Default,
            FaceHandlingOptions(CenterTargetPositionFactory),
            required,
            PlayerLocationOnPlacement(player.position()),
        )
        return runCatching { findBestBlockPlacementTarget(position, options) }.getOrNull()
    }

    private fun requiredItem(
        kind: LitematicaBlockKind,
        state: BlockState,
        schematicWorld: WorldSchematic,
        position: BlockPos,
    ): ItemStack = if (kind == LitematicaBlockKind.AIR) ItemStack.EMPTY else {
        MaterialCache.getInstance().getRequiredBuildItemForState(state, schematicWorld, position).copy()
    }

    private companion object {
        val PLACEABLE_KINDS = setOf(LitematicaBlockKind.SOLID, LitematicaBlockKind.FLUID_SOURCE)
    }
}

internal fun BlockPos.toDomainPosition() = LitematicaPosition(x, y, z)
