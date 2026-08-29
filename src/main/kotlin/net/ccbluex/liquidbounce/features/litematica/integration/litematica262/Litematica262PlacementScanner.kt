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
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementMethod
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanBatch
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanCursor
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockOffsetOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.utils.block.targetfinding.FaceHandlingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.PlayerLocationOnPlacement
import net.ccbluex.liquidbounce.utils.block.targetfinding.findBestBlockPlacementTarget
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

internal class Litematica262PlacementScanner(
    private val nanoTime: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val cursors = linkedMapOf<String, CursorState>()

    fun metadata(): List<LitematicaPlacementMetadataSnapshot> = Litematica262PlacementIndex.capture().metadata

    fun placement(id: LitematicaPlacementId): SchematicPlacement? = Litematica262PlacementIndex.capture().placement(id)

    fun scan(request: LitematicaScanRequest): LitematicaScanBatch {
        val mc = Minecraft.getInstance()
        val world = mc.level ?: return completeBatch()
        val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return completeBatch()
        val index = Litematica262PlacementIndex.capture()
        val resumed = resumeAt(request.cursor, index.fingerprint)
        val cube = resumed?.cube ?: ScanCube.create(request, world)
        var positionIndex = resumed?.nextIndex ?: 0L
        var inspectedPositions = 0
        val startedAt = nanoTime()
        var emittedCells = 0
        val cells = linkedMapOf<LitematicaPlacementId, MutableList<LitematicaCellSnapshot>>()
        val interactions = mutableListOf<LitematicaCellInteractionSnapshot>()

        while (positionIndex < cube.volume) {
            if (inspectedPositions > 0 && shouldYield(request, startedAt, emittedCells)) break
            val position = cube.position(positionIndex++)
            inspectedPositions++
            if (!cube.insideSphere(position)) continue
            for (view in index.views) {
                val desired = view.desiredAt(position) ?: continue
                val cell = createCell(view, position, desired, world, schematicWorld)
                cells.getOrPut(view.metadata.id, ::mutableListOf) += cell.snapshot
                cell.interaction?.let(interactions::add)
                emittedCells++
            }
        }

        val complete = positionIndex >= cube.volume
        val nextCursor = if (complete) null else saveCursor(index.fingerprint, cube, positionIndex)
        val placements = index.views.mapNotNull { view ->
            val localCells = cells[view.metadata.id] ?: return@mapNotNull null
            LitematicaPlacementSnapshot(
                id = view.metadata.id,
                name = view.metadata.name,
                enabled = view.metadata.enabled,
                rendered = view.metadata.rendered,
                bounds = view.metadata.bounds,
                renderLayer = view.metadata.renderLayer,
                cells = localCells,
            )
        }
        return LitematicaScanBatch(
            placements = placements,
            interactions = interactions,
            nextCursor = nextCursor,
            complete = complete,
            restartGeneration = request.cursor != null && resumed == null,
        )
    }

    override fun close() {
        cursors.clear()
    }

    private fun createCell(
        view: Litematica262PlacementView,
        position: BlockPos,
        desiredCell: Litematica262PlacementView.DesiredCell,
        world: ClientLevel,
        schematicWorld: fi.dy.masa.litematica.world.WorldSchematic,
    ): ScannedCell {
        val actualState = world.getBlockState(position)
        val desired = Litematica262BlockSnapshotMapper.snapshot(
            desiredCell.state,
            reproducible = desiredCell.reproducible,
        )
        val actual = Litematica262BlockSnapshotMapper.snapshot(
            actualState,
            hasBlockEntity = actualState.hasBlockEntity() || world.getBlockEntity(position) != null,
        )
        val required = requiredItem(desired.kind, desiredCell.state, schematicWorld, position)
        val materialId = required.takeUnless(ItemStack::isEmpty)?.let {
            BuiltInRegistries.ITEM.getKey(it.item).toString()
        }
        val needsMaterial = desired.kind != LitematicaBlockKind.AIR
        val available = !needsMaterial || !required.isEmpty && Litematica262Inventory.availableCount(required) > 0
        val target = placementTarget(position, required, desired.kind, actual.replaceable)
        val method = when {
            desired.kind != LitematicaBlockKind.SOLID || !actual.replaceable -> LitematicaPlacementMethod.UNAVAILABLE
            required.isEmpty -> LitematicaPlacementMethod.UNAVAILABLE
            target != null -> LitematicaPlacementMethod.NEIGHBOR_FACE
            else -> LitematicaPlacementMethod.AIR_PLACE
        }
        val domainPosition = position.toDomainPosition()
        val snapshot = LitematicaCellSnapshot(
            position = domainPosition,
            desired = desired,
            actual = actual,
            placementMethod = method,
            materialAvailable = available,
            requiredMaterialId = if (available) materialId else materialId ?: desired.id,
        )
        val rotationTarget = target?.interactionPoint ?: Vec3.atCenterOf(position)
        val hasInteraction = method != LitematicaPlacementMethod.UNAVAILABLE ||
            desired.kind == LitematicaBlockKind.FLUID_SOURCE && target != null
        val interaction = if (!hasInteraction) {
            null
        } else {
            LitematicaCellInteractionSnapshot(
                placementId = view.metadata.id,
                position = domainPosition,
                neighborHitResult = target?.blockHitResult,
                rotationTarget = rotationTarget,
            )
        }
        return ScannedCell(snapshot, interaction)
    }

    private fun placementTarget(
        position: BlockPos,
        required: ItemStack,
        kind: LitematicaBlockKind,
        replaceable: Boolean,
    ): net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTarget? {
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
        state: net.minecraft.world.level.block.state.BlockState,
        schematicWorld: fi.dy.masa.litematica.world.WorldSchematic,
        position: BlockPos,
    ): ItemStack = if (kind == LitematicaBlockKind.AIR) {
        ItemStack.EMPTY
    } else {
        MaterialCache.getInstance().getRequiredBuildItemForState(state, schematicWorld, position).copy()
    }

    private fun resumeAt(cursor: LitematicaScanCursor?, fingerprint: Int): CursorState? {
        cursor ?: return null
        val state = cursors.remove(cursor.value) ?: return null
        return state.takeIf { it.fingerprint == fingerprint }
    }

    private fun saveCursor(fingerprint: Int, cube: ScanCube, nextIndex: Long): LitematicaScanCursor {
        while (cursors.size >= MAX_CURSOR_COUNT) cursors.remove(cursors.keys.first())
        val cursor = LitematicaScanCursor(UUID.randomUUID().toString())
        cursors[cursor.value] = CursorState(fingerprint, cube, nextIndex)
        return cursor
    }

    private fun shouldYield(request: LitematicaScanRequest, startedAt: Long, emittedCells: Int): Boolean =
        emittedCells >= request.maxCells || nanoTime() - startedAt >= request.timeBudgetNanos

    private data class ScannedCell(
        val snapshot: LitematicaCellSnapshot,
        val interaction: LitematicaCellInteractionSnapshot?,
    )

    private data class CursorState(
        val fingerprint: Int,
        val cube: ScanCube,
        val nextIndex: Long,
    )

    private companion object {
        const val MAX_CURSOR_COUNT = 32
        val PLACEABLE_KINDS = setOf(LitematicaBlockKind.SOLID, LitematicaBlockKind.FLUID_SOURCE)
        fun completeBatch() = LitematicaScanBatch(emptyList(), emptyList(), null, complete = true)
    }
}

private data class ScanCube(
    val request: LitematicaScanRequest,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Long,
    val sizeY: Long,
    val sizeZ: Long,
) {
    val volume: Long = sizeX * sizeY * sizeZ

    fun position(index: Long): BlockPos {
        val x = index % sizeX
        val z = index / sizeX % sizeZ
        val y = index / (sizeX * sizeZ)
        return BlockPos(minX + x.toInt(), minY + y.toInt(), minZ + z.toInt())
    }

    fun insideSphere(position: BlockPos): Boolean = position.toDomainPosition()
        .distanceSquaredTo(request.center) <= request.range * request.range

    companion object {
        fun create(request: LitematicaScanRequest, world: ClientLevel): ScanCube {
            val minX = ceil(request.center.x - request.range - 0.5).toInt()
            val maxX = floor(request.center.x + request.range - 0.5).toInt()
            val minY = ceil(request.center.y - request.range - 0.5).toInt().coerceAtLeast(world.minY)
            val maxY = floor(request.center.y + request.range - 0.5).toInt().coerceAtMost(world.maxY)
            val minZ = ceil(request.center.z - request.range - 0.5).toInt()
            val maxZ = floor(request.center.z + request.range - 0.5).toInt()
            return ScanCube(
                request,
                minX,
                minY,
                minZ,
                (maxX - minX + 1).coerceAtLeast(0).toLong(),
                (maxY - minY + 1).coerceAtLeast(0).toLong(),
                (maxZ - minZ + 1).coerceAtLeast(0).toLong(),
            )
        }
    }
}

private fun BlockPos.toDomainPosition() = LitematicaPosition(x, y, z)
