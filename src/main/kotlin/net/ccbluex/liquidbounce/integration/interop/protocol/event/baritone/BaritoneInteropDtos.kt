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


@file:JvmName("BaritoneEventsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone

import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.event.WebSocketEvent

@JvmRecord
data class BaritonePointDto(val x: Double, val y: Double, val z: Double)

@JvmRecord
data class BaritoneRouteDto(val revision: Long, val points: List<BaritonePointDto>)

enum class BaritoneAvailabilityDto {
    AVAILABLE,
    UNAVAILABLE,
}

enum class BaritonePhaseDto {
    UNAVAILABLE,
    NO_WORLD,
    IDLE,
    CALCULATING,
    PATHING,
    PAUSED,
    FAILED,
    ARRIVED,
}

enum class BaritoneTaskTypeDto {
    GOTO,
    GET_TO_BLOCK,
    MINE,
    FOLLOW,
    FARM,
    EXPLORE,
    BUILD,
    ELYTRA,
}

enum class BaritoneSettingTypeDto {
    BOOLEAN,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    ENUM,
    STRING_LIST,
}

enum class BaritoneLogLevelDto {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

enum class BaritoneLocomotionDto {
    FLY,
    WALK,
}

enum class BaritoneNavigationPhaseDto {
    IDLE,
    WAITING_FOR_PATH,
    PLANNING,
    ARMING,
    FLYING,
    WALK_FALLBACK,
    WAITING_FOR_USER,
}

enum class BaritoneFlyOwnershipDto {
    BARITONE,
    USER,
}

@JvmRecord
data class BaritoneNavigationDto(
    val requested: BaritoneLocomotionDto = BaritoneLocomotionDto.FLY,
    val active: BaritoneLocomotionDto? = null,
    val phase: BaritoneNavigationPhaseDto = BaritoneNavigationPhaseDto.IDLE,
    val flyMode: String? = null,
    val ownership: BaritoneFlyOwnershipDto? = null,
    val detail: String? = null,
    val restartsRemaining: Int = BaritoneNavigationSnapshot.DEFAULT_MAX_RESTARTS,
)

@JvmRecord
data class BaritoneTaskSummaryDto(
    val type: BaritoneTaskTypeDto,
    val label: String,
    val details: String? = null,
)

@JvmRecord
data class BaritoneSettingDto(
    val name: String,
    val type: BaritoneSettingTypeDto,
    val value: Any,
    val defaultValue: Any,
    val description: String,
    val mutable: Boolean,
    val options: List<String>? = null,
)

@JvmRecord
data class BaritoneWaypointDto(
    val id: String,
    val name: String,
    val tag: String? = null,
    val position: BaritonePointDto,
)

@JvmRecord
data class BaritoneLogEntryDto(
    val revision: Long,
    val level: BaritoneLogLevelDto,
    val message: String,
    val timestamp: String,
)

@JvmRecord
data class BaritoneSnapshotDto(
    val revision: Long,
    val availability: BaritoneAvailabilityDto,
    val status: BaritonePhaseDto,
    val task: BaritoneTaskSummaryDto?,
    val etaSeconds: Double?,
    val progress: Double?,
    val pauseReason: String?,
    val settings: List<BaritoneSettingDto>,
    val waypoints: List<BaritoneWaypointDto>,
    val logs: List<BaritoneLogEntryDto>,
    val failure: String? = null,
    val navigation: BaritoneNavigationDto = BaritoneNavigationDto(),
)

@Tag("baritoneState")
class BaritoneStateEvent(val revision: Long, val snapshot: BaritoneSnapshotDto) : Event(), WebSocketEvent {
    init {
        requireMatchingRevision(revision, snapshot.revision)
    }
}

@Tag("baritoneRoute")
class BaritoneRouteEvent(val revision: Long, val route: BaritoneRouteDto) : Event(), WebSocketEvent {
    init {
        requireMatchingRevision(revision, route.revision)
    }
}

@Tag("baritoneLog")
class BaritoneLogEvent(val revision: Long, val entry: BaritoneLogEntryDto) : Event(), WebSocketEvent {
    init {
        requireMatchingRevision(revision, entry.revision)
    }
}

internal fun requireMatchingRevision(eventRevision: Long, payloadRevision: Long) {
    require(eventRevision >= 0) { "Baritone event revision must not be negative" }
    require(eventRevision == payloadRevision) { "Baritone event and payload revisions must match" }
}

internal fun BaritoneRoute.toInteropDto() = BaritoneRouteDto(
    revision = revision.value,
    points = points.map { point -> BaritonePointDto(point.x, point.y, point.z) },
)

internal fun BaritoneSnapshot.toInteropDto() = BaritoneSnapshotDto(
    revision = revision.value,
    availability = availability.toInteropDto(),
    status = BaritonePhaseDto.valueOf(status.name),
    task = task?.toInteropDto(),
    etaSeconds = etaSeconds?.toDouble(),
    progress = progress?.fraction,
    pauseReason = pauseReason?.let { cause ->
        cause.owner?.let { owner -> "${cause.reason.name}: $owner" } ?: cause.reason.name
    },
    settings = settings.map(BaritoneSetting::toInteropDto),
    waypoints = waypoints.map(BaritoneWaypoint::toInteropDto),
    logs = logs.map(BaritoneLogEntry::toInteropDto),
    failure = failure?.message,
    navigation = navigation.toInteropDto(),
)
