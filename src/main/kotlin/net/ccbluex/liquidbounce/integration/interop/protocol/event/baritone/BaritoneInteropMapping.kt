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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCapability
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import java.time.Instant

internal fun BaritoneNavigationSnapshot.toInteropDto() = BaritoneNavigationDto(
    requested = requestedMode.toInteropDto(),
    active = activeMode?.toInteropDto(),
    phase = BaritoneNavigationPhaseDto.valueOf(phase.name),
    flyMode = flyMode,
    ownership = flyOwnership?.toInteropDto(),
    detail = detail,
    restartsRemaining = restartsRemaining,
)

internal fun BaritoneSetting.toInteropDto() = BaritoneSettingDto(
    name = name.value,
    type = type.toInteropDto(),
    value = value.toInteropValue(),
    defaultValue = defaultValue.toInteropValue(),
    description = description,
    mutable = mutable,
    options = options.takeIf { it.isNotEmpty() },
)

internal fun BaritoneWaypoint.toInteropDto() = BaritoneWaypointDto(
    id = id.value,
    name = name,
    tag = tag?.name,
    position = position.let { point ->
        BaritonePointDto(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
    },
)

internal fun BaritoneLogEntry.toInteropDto() = BaritoneLogEntryDto(
    revision = revision.value,
    level = when (level) {
        BaritoneLogLevel.DEBUG -> BaritoneLogLevelDto.DEBUG
        BaritoneLogLevel.INFO -> BaritoneLogLevelDto.INFO
        BaritoneLogLevel.WARNING -> BaritoneLogLevelDto.WARNING
        BaritoneLogLevel.ERROR -> BaritoneLogLevelDto.ERROR
    },
    message = message,
    timestamp = Instant.ofEpochMilli(timestamp).toString(),
)

internal fun BaritoneCapability.toInteropDto() = when (this) {
    BaritoneCapability.AVAILABLE -> BaritoneAvailabilityDto.AVAILABLE
    BaritoneCapability.UNAVAILABLE -> BaritoneAvailabilityDto.UNAVAILABLE
}

internal fun BaritoneNavigationMode.toInteropDto() = BaritoneLocomotionDto.valueOf(name)

internal fun BaritoneFlyOwnership.toInteropDto() = BaritoneFlyOwnershipDto.valueOf(name)

internal fun BaritoneSettingType.toInteropDto() = when (this) {
    BaritoneSettingType.BOOLEAN -> BaritoneSettingTypeDto.BOOLEAN
    BaritoneSettingType.INTEGER -> BaritoneSettingTypeDto.INTEGER
    BaritoneSettingType.LONG -> BaritoneSettingTypeDto.LONG
    BaritoneSettingType.DECIMAL -> BaritoneSettingTypeDto.DOUBLE
    BaritoneSettingType.STRING -> BaritoneSettingTypeDto.STRING
    BaritoneSettingType.ENUM -> BaritoneSettingTypeDto.ENUM
    BaritoneSettingType.STRING_LIST -> BaritoneSettingTypeDto.STRING_LIST
}

internal fun BaritoneSettingValue.toInteropValue(): Any = when (this) {
    is BaritoneSettingValue.BooleanValue -> value
    is BaritoneSettingValue.IntegerValue -> value
    is BaritoneSettingValue.LongValue -> value
    is BaritoneSettingValue.DecimalValue -> value
    is BaritoneSettingValue.TextValue -> value
    is BaritoneSettingValue.EnumValue -> value
    is BaritoneSettingValue.StringListValue -> values
}

internal fun BaritoneTaskRequest.toInteropDto() = BaritoneTaskSummaryDto(
    type = BaritoneTaskTypeDto.valueOf(kind.name),
    label = taskLabel(),
    details = taskDetails(),
)

internal fun BaritoneTaskRequest.taskLabel(): String = when (this) {
    is BaritoneTaskRequest.GoTo -> "Go to ${goal.describe()}"
    is BaritoneTaskRequest.GetToBlock -> "Get to ${block.value}"
    is BaritoneTaskRequest.Mine -> "Mine $quantity block${if (quantity == 1) "" else "s"}"
    is BaritoneTaskRequest.Follow -> "Follow $player"
    is BaritoneTaskRequest.Farm -> "Farm"
    is BaritoneTaskRequest.Explore -> "Explore"
    is BaritoneTaskRequest.Build -> "Build $schematic"
    is BaritoneTaskRequest.Elytra -> "Elytra to ${destination.x} ${destination.y} ${destination.z}"
}

internal fun BaritoneTaskRequest.taskDetails(): String? = when (this) {
    is BaritoneTaskRequest.GoTo -> null
    is BaritoneTaskRequest.GetToBlock -> block.value
    is BaritoneTaskRequest.Mine -> blocks.joinToString { it.value }
    is BaritoneTaskRequest.Follow -> "Within $radius blocks"
    is BaritoneTaskRequest.Farm -> "Radius $radius${center?.let { " around ${it.x} ${it.y} ${it.z}" }.orEmpty()}"
    is BaritoneTaskRequest.Explore -> radius?.let { "Radius $it" }
    is BaritoneTaskRequest.Build -> origin?.let { "At ${it.x} ${it.y} ${it.z}" }
    is BaritoneTaskRequest.Elytra -> null
}

internal fun BaritoneGoal.describe(): String = when (this) {
    is BaritoneGoal.Block -> "${position.x} ${position.y} ${position.z}"
    is BaritoneGoal.Horizontal -> "${position.x} ${position.z}"
    is BaritoneGoal.Level -> "Y $y"
    is BaritoneGoal.Near -> "${position.x} ${position.y} ${position.z} within $radius"
}
