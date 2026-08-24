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
import baritone.api.cache.IWaypoint
import baritone.api.cache.Waypoint
import baritone.api.utils.BetterBlockPos
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointTag
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal class BaritoneWaypointAdapter(private val baritone: IBaritone) {

    fun waypoints(): List<BaritoneWaypoint> = collection()?.allWaypoints.orEmpty()
        .sortedWith(compareByDescending<IWaypoint> { it.creationTimestamp }.thenBy { it.name })
        .map(::toCore)

    fun add(draft: BaritoneWaypointDraft): BaritoneWaypoint {
        val collection = collection() ?: noWorld()
        val position = draft.position
        val waypoint = Waypoint(
            draft.name,
            draft.tag.toUpstream(),
            BetterBlockPos(position.x, position.y, position.z),
        )
        collection.addWaypoint(waypoint)
        return toCore(waypoint)
    }

    fun delete(selector: BaritoneWaypointSelector) {
        val collection = collection() ?: noWorld()
        val waypoint = collection.allWaypoints
            .filter { waypoint -> selector.matches(waypoint) }
            .maxByOrNull(IWaypoint::getCreationTimestamp)
            ?: throw BaritoneAdapterException(BaritoneErrorCode.NOT_FOUND, "Waypoint was not found", "waypoint")
        collection.removeWaypoint(waypoint)
    }

    private fun collection() = baritone.worldProvider.currentWorld?.waypoints

    private fun toCore(waypoint: IWaypoint): BaritoneWaypoint {
        val position = waypoint.location
        return BaritoneWaypoint(
            id = BaritoneWaypointId(waypoint.stableId()),
            name = waypoint.name,
            tag = waypoint.tag.toCore(),
            position = BaritoneBlockPosition(position.x, position.y, position.z),
        )
    }

    private fun BaritoneWaypointSelector.matches(waypoint: IWaypoint): Boolean = when (this) {
        is BaritoneWaypointSelector.ById -> id.value == waypoint.stableId()
        is BaritoneWaypointSelector.ByName -> name.equals(waypoint.name, ignoreCase = true)
    }

    private fun IWaypoint.stableId(): String {
        val position = location
        val source = "$creationTimestamp\u0000${tag.name}\u0000$name\u0000${position.x},${position.y},${position.z}"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(18))
    }

    private fun noWorld(): Nothing = throw BaritoneAdapterException(
        BaritoneErrorCode.INVALID_STATE,
        "No Minecraft world is loaded",
    )
}

private fun BaritoneWaypointTag?.toUpstream(): IWaypoint.Tag = when (this) {
    BaritoneWaypointTag.HOME -> IWaypoint.Tag.HOME
    BaritoneWaypointTag.DEATH -> IWaypoint.Tag.DEATH
    BaritoneWaypointTag.BED -> IWaypoint.Tag.BED
    BaritoneWaypointTag.USER, null -> IWaypoint.Tag.USER
}

private fun IWaypoint.Tag.toCore(): BaritoneWaypointTag = when (this) {
    IWaypoint.Tag.HOME -> BaritoneWaypointTag.HOME
    IWaypoint.Tag.DEATH -> BaritoneWaypointTag.DEATH
    IWaypoint.Tag.BED -> BaritoneWaypointTag.BED
    IWaypoint.Tag.USER -> BaritoneWaypointTag.USER
}
