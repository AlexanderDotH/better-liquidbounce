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

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanBatch
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanCursor
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import java.util.UUID

internal class Litematica262PlacementScanner(
    private val nanoTime: () -> Long = System::nanoTime,
) : AutoCloseable {
    private val cursors = linkedMapOf<String, CursorState>()
    private val cellFactory = Litematica262CellFactory()

    fun metadata(): List<LitematicaPlacementMetadataSnapshot> = Litematica262PlacementIndex.capture().metadata

    fun placement(id: LitematicaPlacementId): SchematicPlacement? = Litematica262PlacementIndex.capture().placement(id)

    fun scan(request: LitematicaScanRequest): LitematicaScanBatch {
        val mc = Minecraft.getInstance()
        val world = mc.level ?: return completeBatch()
        val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return completeBatch()
        val index = Litematica262PlacementIndex.capture()
        val resumed = resumeAt(request.cursor, index.fingerprint)
        val cube = resumed?.cube ?: Litematica262ScanCube.create(request, world)
        val progress = scanPositions(
            request, cube, resumed?.nextIndex ?: 0L, index, world, schematicWorld,
        )
        val complete = progress.nextIndex >= cube.volume
        val nextCursor = if (complete) null else saveCursor(index.fingerprint, cube, progress.nextIndex)
        val placements = index.views.mapNotNull { view ->
            val localCells = progress.cells[view.metadata.id] ?: return@mapNotNull null
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
            interactions = progress.interactions,
            nextCursor = nextCursor,
            complete = complete,
            restartGeneration = request.cursor != null && resumed == null,
        )
    }

    private fun scanPositions(
        request: LitematicaScanRequest,
        cube: Litematica262ScanCube,
        startIndex: Long,
        index: Litematica262PlacementIndex,
        world: ClientLevel,
        schematicWorld: fi.dy.masa.litematica.world.WorldSchematic,
    ): ScanProgress {
        val progress = ScanProgress(startIndex)
        val startedAt = nanoTime()
        while (progress.nextIndex < cube.volume) {
            if (progress.inspected > 0 && shouldYield(request, startedAt, progress.emitted)) break
            val position = cube.position(progress.nextIndex++)
            progress.inspected++
            if (!cube.insideSphere(position)) continue
            scanPosition(position, index, world, schematicWorld, progress)
        }
        return progress
    }

    private fun scanPosition(
        position: BlockPos,
        index: Litematica262PlacementIndex,
        world: ClientLevel,
        schematicWorld: fi.dy.masa.litematica.world.WorldSchematic,
        progress: ScanProgress,
    ) {
        for (view in index.views) {
            val desired = view.desiredAt(position) ?: continue
            val cell = cellFactory.create(view, position, desired, world, schematicWorld)
            progress.cells.getOrPut(view.metadata.id, ::mutableListOf) += cell.snapshot
            cell.interaction?.let(progress.interactions::add)
            progress.emitted++
        }
    }

    override fun close() {
        cursors.clear()
    }

    private fun resumeAt(cursor: LitematicaScanCursor?, fingerprint: Int): CursorState? {
        cursor ?: return null
        val state = cursors.remove(cursor.value) ?: return null
        return state.takeIf { it.fingerprint == fingerprint }
    }

    private fun saveCursor(fingerprint: Int, cube: Litematica262ScanCube, nextIndex: Long): LitematicaScanCursor {
        while (cursors.size >= MAX_CURSOR_COUNT) cursors.remove(cursors.keys.first())
        val cursor = LitematicaScanCursor(UUID.randomUUID().toString())
        cursors[cursor.value] = CursorState(fingerprint, cube, nextIndex)
        return cursor
    }

    private fun shouldYield(request: LitematicaScanRequest, startedAt: Long, emittedCells: Int): Boolean =
        emittedCells >= request.maxCells || nanoTime() - startedAt >= request.timeBudgetNanos

    private data class CursorState(
        val fingerprint: Int,
        val cube: Litematica262ScanCube,
        val nextIndex: Long,
    )

    private data class ScanProgress(
        var nextIndex: Long,
        var inspected: Int = 0,
        var emitted: Int = 0,
        val cells: MutableMap<LitematicaPlacementId, MutableList<LitematicaCellSnapshot>> = linkedMapOf(),
        val interactions: MutableList<LitematicaCellInteractionSnapshot> = mutableListOf(),
    )

    private companion object {
        const val MAX_CURSOR_COUNT = 32
        fun completeBatch() = LitematicaScanBatch(emptyList(), emptyList(), null, complete = true)
    }
}
