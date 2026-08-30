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

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

internal data class SeedMismatchRenderSettings(
    val maximumDistance: Double,
    val renderLimit: Int,
    val missingSolidColor: Color4b,
    val unexpectedSolidColor: Color4b,
    val utilityMismatchColor: Color4b,
    val materialSwapColor: Color4b,
)

internal data class SeedMismatchRenderEntry(
    val cell: SeedMismatchCell,
    val distance: Double,
    val worldBox: AABB,
    val cameraRelativeBox: AABB,
    val color: Color4b,
) {
    val faceColor: Color4b get() = color.with(a = 32)
    val outlineColor: Color4b get() = color.with(a = 180)
    val glowMaskColor: Color4b get() = color.with(a = 255)
}

internal data class SeedMismatchRenderBatch(val entries: List<SeedMismatchRenderEntry>) {
    companion object {
        val EMPTY = SeedMismatchRenderBatch(emptyList())
    }
}

internal object BaseFinderSeedMismatchRenderPlanner {
    fun plan(
        cells: Collection<SeedMismatchCell>,
        cameraPosition: Vec3,
        settings: SeedMismatchRenderSettings,
    ): SeedMismatchRenderBatch {
        if (cells.isEmpty() || settings.renderLimit <= 0 || settings.maximumDistance < 0.0) {
            return SeedMismatchRenderBatch.EMPTY
        }
        val entries = selectEntries(cells, cameraPosition, settings)
        return if (entries.isEmpty()) SeedMismatchRenderBatch.EMPTY
        else SeedMismatchRenderBatch(java.util.List.copyOf(entries))
    }

    private fun selectEntries(
        cells: Collection<SeedMismatchCell>,
        cameraPosition: Vec3,
        settings: SeedMismatchRenderSettings,
    ): List<SeedMismatchRenderEntry> {
        val maximumDistanceSq = settings.maximumDistance * settings.maximumDistance
        return cells.asSequence()
            .map { cell -> cell to cell.center().distanceToSqr(cameraPosition) }
            .filter { (_, distanceSq) -> distanceSq <= maximumDistanceSq }
            .sortedWith(cellDistanceComparator)
            .take(settings.renderLimit)
            .map { (cell, distanceSq) -> createEntry(cell, distanceSq, cameraPosition, settings) }
            .toList()
    }

    private fun createEntry(
        cell: SeedMismatchCell,
        distanceSq: Double,
        cameraPosition: Vec3,
        settings: SeedMismatchRenderSettings,
    ): SeedMismatchRenderEntry {
        val worldBox = AABB(
            cell.position.x.toDouble(), cell.position.y.toDouble(), cell.position.z.toDouble(),
            cell.position.x + 1.0, cell.position.y + 1.0, cell.position.z + 1.0,
        )
        return SeedMismatchRenderEntry(
            cell, sqrt(distanceSq), worldBox, worldBox.move(cameraPosition.reverse()), colorFor(cell.kind, settings),
        )
    }

    private fun SeedMismatchCell.center() = Vec3(position.x + 0.5, position.y + 0.5, position.z + 0.5)

    private val cellDistanceComparator = compareBy<Pair<SeedMismatchCell, Double>> { it.second }
        .thenBy { it.first.position.y }
        .thenBy { it.first.position.x }
        .thenBy { it.first.position.z }

    private fun colorFor(kind: SeedMismatchKind, settings: SeedMismatchRenderSettings): Color4b = when (kind) {
        SeedMismatchKind.MISSING_SOLID -> settings.missingSolidColor
        SeedMismatchKind.UNEXPECTED_SOLID -> settings.unexpectedSolidColor
        SeedMismatchKind.UTILITY -> settings.utilityMismatchColor
        SeedMismatchKind.MATERIAL_SWAP -> settings.materialSwapColor
    }
}
