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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector

internal fun BaritoneAdapterContext.adapterSettings(): List<BaritoneSetting> =
    settingsConfig.settings().map(NativeBaritoneSetting::toCoreSetting)

internal fun BaritoneAdapterContext.adapterSetting(name: BaritoneSettingName): BaritoneSetting? =
    settingsConfig.setting(name.value)?.toCoreSetting()

internal fun BaritoneAdapterContext.updateAdapterSetting(
    name: BaritoneSettingName,
    value: BaritoneSettingValue,
): BaritoneResult<BaritoneSetting> = executeAdapterOperation(name.value) {
    val previous = adapterSetting(name) ?: throw adapterSettingNotFound(name)
    if (!previous.mutable) {
        throw BaritoneAdapterException(
            BaritoneErrorCode.INVALID_STATE,
            "Baritone setting '${name.value}' is managed by LiquidBounce",
            name.value,
        )
    }
    if (previous.type != value.type) {
        throw BaritoneAdapterException(
            BaritoneErrorCode.INVALID_FIELD,
            "Expected ${previous.type} but received ${value.type}",
            name.value,
        )
    }
    settingsConfig.update(name.value, value.toUpstreamString()).getOrThrow().toCoreSetting()
}

internal fun BaritoneAdapterContext.resetAdapterSetting(
    name: BaritoneSettingName,
): BaritoneResult<BaritoneSetting> = executeAdapterOperation(name.value) {
    settingsConfig.reset(name.value).getOrElse { throw adapterSettingNotFound(name, it) }.toCoreSetting()
}

internal fun BaritoneAdapterContext.resetAdapterSettings(): BaritoneResult<List<BaritoneSetting>> =
    executeAdapterOperation { settingsConfig.resetAllSettings().map(NativeBaritoneSetting::toCoreSetting) }

internal fun BaritoneAdapterContext.deleteAdapterSetting(name: BaritoneSettingName): BaritoneResult<Unit> =
    executeAdapterOperation(name.value) {
        settingsConfig.delete(name.value).getOrElse { throw adapterSettingNotFound(name, it) }
    }

internal fun BaritoneAdapterContext.adapterWaypoints(): List<BaritoneWaypoint> =
    runCatching(waypointAdapter::waypoints).getOrDefault(emptyList())

internal fun BaritoneAdapterContext.addAdapterWaypoint(
    waypoint: BaritoneWaypointDraft,
): BaritoneResult<BaritoneWaypoint> = executeAdapterOperation("waypoint") { waypointAdapter.add(waypoint) }

internal fun BaritoneAdapterContext.deleteAdapterWaypoint(
    selector: BaritoneWaypointSelector,
): BaritoneResult<Unit> = executeAdapterOperation("waypoint") { waypointAdapter.delete(selector) }
