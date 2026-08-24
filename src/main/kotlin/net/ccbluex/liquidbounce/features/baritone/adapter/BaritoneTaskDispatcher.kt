/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.IBaritone
import baritone.api.utils.BlockOptionalMeta
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.minecraft.core.BlockPos
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

internal class BaritoneTaskDispatcher(
    private val baritone: IBaritone,
    private val settings: BaritoneSettingsConfig,
    private val schematicPaths: SchematicPathPolicy,
    private val runtimeDirectory: Path,
) {

    fun submit(task: BaritoneTaskRequest) {
        when (task) {
            is BaritoneTaskRequest.GoTo -> baritone.customGoalProcess.setGoalAndPath(
                BaritoneGoalMapper.toUpstream(task.goal)
            )
            is BaritoneTaskRequest.GetToBlock -> submitGetToBlock(task)
            is BaritoneTaskRequest.Mine -> submitMine(task)
            is BaritoneTaskRequest.Follow -> submitFollow(task)
            is BaritoneTaskRequest.Farm -> submitFarm(task)
            is BaritoneTaskRequest.Explore -> submitExplore(task)
            is BaritoneTaskRequest.Build -> submitBuild(task)
            is BaritoneTaskRequest.Elytra -> submitElytra(task)
        }
    }

    private fun submitGetToBlock(task: BaritoneTaskRequest.GetToBlock) {
        val block = runCatching { BlockOptionalMeta(task.block.value) }.getOrElse {
            throw BaritoneAdapterException(BaritoneErrorCode.INVALID_FIELD, it.message.orEmpty(), "block", it)
        }
        baritone.getToBlockProcess.getToBlock(block)
    }

    private fun submitMine(task: BaritoneTaskRequest.Mine) {
        baritone.mineProcess.mineByName(task.quantity, *task.blocks.map { it.value }.toTypedArray())
    }

    private fun submitFollow(task: BaritoneTaskRequest.Follow) {
        val radius = ceil(task.radius).toInt().coerceAtLeast(1)
        settings.update("followRadius", radius.toString()).getOrElse {
            throw BaritoneAdapterException(BaritoneErrorCode.INVALID_FIELD, it.message.orEmpty(), "radius", it)
        }
        baritone.followProcess.follow { entity -> entity.scoreboardName.equals(task.player, ignoreCase = true) }
    }

    private fun submitFarm(task: BaritoneTaskRequest.Farm) {
        baritone.farmProcess.farm(task.radius, task.center?.let {
            BlockPos(it.x, it.y, it.z)
        })
    }

    private fun submitExplore(task: BaritoneTaskRequest.Explore) {
        val origin = task.origin ?: baritone.playerContext.playerFeet().let {
            BaritoneHorizontalPosition(it.x, it.z)
        }
        applyExploreFilter(origin, task.radius)
        baritone.exploreProcess.explore(origin.x, origin.z)
    }

    private fun applyExploreFilter(origin: BaritoneHorizontalPosition, radius: Int?) {
        val chunks = radius?.let { BaritoneExploreFilter.chunks(origin, it) }.orEmpty()
        Files.createDirectories(runtimeDirectory)
        val filter = Files.createTempFile(runtimeDirectory, "liquidbounce-explore-", ".json")
        try {
            Files.writeString(filter, BaritoneExploreFilter.json(chunks))
            baritone.exploreProcess.applyJsonFilter(filter, radius != null)
        } finally {
            Files.deleteIfExists(filter)
        }
    }

    private fun submitBuild(task: BaritoneTaskRequest.Build) {
        val schematic = runCatching { schematicPaths.resolveExisting(task.schematic) }.getOrElse {
            throw BaritoneAdapterException(BaritoneErrorCode.INVALID_FIELD, it.message.orEmpty(), "schematic", it)
        }
        val origin = task.origin?.let { BlockPos(it.x, it.y, it.z) } ?: baritone.playerContext.playerFeet()
        if (!baritone.builderProcess.build(schematic.fileName.toString(), schematic.toFile(), origin)) {
            throw BaritoneAdapterException(
                BaritoneErrorCode.INVALID_FIELD,
                "Baritone could not load the schematic",
                "schematic",
            )
        }
    }

    private fun submitElytra(task: BaritoneTaskRequest.Elytra) {
        if (!baritone.elytraProcess.isLoaded) {
            throw BaritoneAdapterException(
                BaritoneErrorCode.UNSUPPORTED,
                "Baritone's native Elytra pathfinder is unavailable",
                "kind",
            )
        }
        baritone.elytraProcess.pathTo(BlockPos(task.destination.x, task.destination.y, task.destination.z))
    }
}

internal class BaritoneAdapterException(
    val code: BaritoneErrorCode,
    override val message: String,
    val field: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message.ifBlank { code.name }, cause)
