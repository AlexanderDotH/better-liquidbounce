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


@file:JvmName("BaritoneFunctionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonElement
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneHorizontalPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNamespacedId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskKind
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointTag
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.toInteropDto
import java.util.Locale

internal data class TaskBody(
    val type: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
    val radius: Double? = null,
    val block: String? = null,
    val blocks: List<String>? = null,
    val quantity: Int? = null,
    val count: Int? = null,
    val player: String? = null,
    val schematic: String? = null,
    val file: String? = null,
) {
    fun toDomain(): BaritoneTaskRequest {
        val kind = parseTaskKind(type)
        return when (kind) {
            BaritoneTaskKind.GOTO -> BaritoneTaskRequest.GoTo(goal())
            BaritoneTaskKind.GET_TO_BLOCK -> BaritoneTaskRequest.GetToBlock(namespacedId("block", block))
            BaritoneTaskKind.MINE -> mineTask()
            BaritoneTaskKind.FOLLOW -> followTask()
            BaritoneTaskKind.FARM -> farmTask()
            BaritoneTaskKind.EXPLORE -> exploreTask()
            BaritoneTaskKind.BUILD -> buildTask()
            BaritoneTaskKind.ELYTRA -> BaritoneTaskRequest.Elytra(blockPosition(required = true)!!)
        }
    }

    private fun goal(): BaritoneGoal = when {
        radius != null -> BaritoneGoal.Near(blockPosition(required = true)!!, positiveWholeRadius())
        x != null && y != null && z != null -> BaritoneGoal.Block(BaritoneBlockPosition(x, y, z))
        x != null && y == null && z != null -> BaritoneGoal.Horizontal(BaritoneHorizontalPosition(x, z))
        x == null && y != null && z == null -> BaritoneGoal.Level(y)
        else -> invalidField("coordinates", "GOTO requires XYZ, XZ, Y, or XYZ with a positive radius")
    }

    private fun mineTask(): BaritoneTaskRequest {
        val requestedBlocks = blocks ?: block?.let(::listOf)
            ?: invalidField("blocks", "MINE requires at least one block")
        val ids = requestedBlocks.map { namespacedId("blocks", it) }
        return domainField("quantity") { BaritoneTaskRequest.Mine(ids, quantity ?: count ?: 1) }
    }

    private fun followTask(): BaritoneTaskRequest {
        val target = player?.takeIf(String::isNotBlank)
            ?: invalidField("player", "FOLLOW requires a player name")
        return domainField("radius") { BaritoneTaskRequest.Follow(target, radius ?: 2.0) }
    }

    private fun farmTask(): BaritoneTaskRequest = domainField("radius") {
        BaritoneTaskRequest.Farm(blockPosition(required = false), positiveWholeRadius(default = 64))
    }

    private fun exploreTask(): BaritoneTaskRequest = domainField("radius") {
        BaritoneTaskRequest.Explore(horizontalPosition(), if (radius == null) null else positiveWholeRadius())
    }

    private fun buildTask(): BaritoneTaskRequest {
        val path = (schematic ?: file)?.takeIf(String::isNotBlank)
            ?: invalidField("schematic", "BUILD requires a schematic path")
        return domainField("schematic") { BaritoneTaskRequest.Build(path, blockPosition(required = false)) }
    }

    private fun blockPosition(required: Boolean): BaritoneBlockPosition? {
        val supplied = x != null || y != null || z != null
        if (!supplied && !required) {
            return null
        }
        if (x == null || y == null || z == null) {
            invalidField("coordinates", "Expected complete x, y, and z coordinates")
        }
        return BaritoneBlockPosition(x, y, z)
    }

    private fun horizontalPosition(): BaritoneHorizontalPosition? {
        if (x == null && z == null) {
            return null
        }
        if (x == null || z == null) {
            invalidField("coordinates", "Expected both x and z coordinates")
        }
        return BaritoneHorizontalPosition(x, z)
    }

    private fun positiveWholeRadius(default: Int? = null): Int {
        val value = radius ?: return default ?: invalidField("radius", "A radius is required")
        if (!value.isFinite() || value <= 0 || value % 1.0 != 0.0 || value > Int.MAX_VALUE) {
            invalidField("radius", "Radius must be a positive whole number")
        }
        return value.toInt()
    }
}

internal data class ControlBody(val action: String? = null) {
    fun toDomain(): BaritoneControlAction {
        val normalized = action?.trim()?.uppercase(Locale.ROOT)
            ?: invalidField("action", "A control action is required")
        return runCatching { BaritoneControlAction.valueOf(normalized) }
            .getOrElse { invalidField("action", "Unsupported control action '$action'") }
    }
}

internal data class SettingBody(val value: JsonElement? = null)

internal data class WaypointBody(
    val name: String? = null,
    val tag: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val z: Int? = null,
) {
    fun toDraft(): BaritoneWaypointDraft {
        val waypointName = name?.takeIf(String::isNotBlank)
            ?: invalidField("name", "A waypoint name is required")
        val position = if (x == null || y == null || z == null) {
            invalidField("coordinates", "A waypoint requires x, y, and z")
        } else {
            BaritoneBlockPosition(x, y, z)
        }
        val waypointTag = tag?.let { raw ->
            runCatching { BaritoneWaypointTag.valueOf(raw.uppercase(Locale.ROOT)) }
                .getOrElse { invalidField("tag", "Unsupported waypoint tag '$raw'") }
        } ?: BaritoneWaypointTag.USER
        return domainField("name") { BaritoneWaypointDraft(waypointName, waypointTag, position) }
    }
}

internal data class WaypointSelectorBody(val id: String? = null, val name: String? = null) {
    fun toDomain(): BaritoneWaypointSelector = when {
        !id.isNullOrBlank() && name.isNullOrBlank() -> domainField("id") {
            BaritoneWaypointSelector.ById(BaritoneWaypointId(id))
        }
        id.isNullOrBlank() && !name.isNullOrBlank() -> domainField("name") {
            BaritoneWaypointSelector.ByName(name)
        }
        else -> invalidField("id", "Specify exactly one waypoint id or name")
    }
}

internal data class CommandBody(val command: String? = null) {
    fun requiredCommand(): String = command?.takeIf(String::isNotBlank)
        ?: invalidField("command", "A command is required")
}

@JvmRecord
internal data class CommandResponse(val accepted: Boolean, val output: String?)

internal fun parseTaskKind(raw: String?): BaritoneTaskKind {
    val normalized = raw?.trim()?.uppercase(Locale.ROOT)
        ?: invalidField("type", "A task type is required")
    return runCatching { BaritoneTaskKind.valueOf(normalized) }
        .getOrElse { invalidField("type", "Unsupported task type '$raw'") }
}

internal fun namespacedId(field: String, raw: String?): BaritoneNamespacedId {
    val value = raw?.takeIf(String::isNotBlank)
        ?: invalidField(field, "A namespaced block id is required")
    return domainField(field) { BaritoneNamespacedId(value) }
}
