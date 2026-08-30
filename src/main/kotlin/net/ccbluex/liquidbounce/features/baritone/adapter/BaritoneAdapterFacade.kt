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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCapability
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector

internal class BaritoneAdapterFacade(
    private val context: BaritoneAdapterContext,
) : BaritoneFacade {

    override fun capability() = BaritoneCapability.AVAILABLE

    override fun snapshot() = context.locked { context.adapterSnapshot() }

    override fun route() = context.locked { context.currentRoute }

    override fun submitTask(task: BaritoneTaskRequest) = context.locked { context.submitAdapterTask(task) }

    override fun control(action: BaritoneControlAction) = context.locked { context.controlAdapter(action) }

    override fun settings() = context.locked { context.adapterSettings() }

    override fun setting(name: BaritoneSettingName) = context.locked { context.adapterSetting(name) }

    override fun updateSetting(name: BaritoneSettingName, value: BaritoneSettingValue) =
        context.locked { context.updateAdapterSetting(name, value) }

    override fun resetSetting(name: BaritoneSettingName) = context.locked { context.resetAdapterSetting(name) }

    override fun resetSettings() = context.locked { context.resetAdapterSettings() }

    override fun deleteSetting(name: BaritoneSettingName) = context.locked { context.deleteAdapterSetting(name) }

    override fun waypoints() = context.locked { context.adapterWaypoints() }

    override fun addWaypoint(waypoint: BaritoneWaypointDraft) = context.locked { context.addAdapterWaypoint(waypoint) }

    override fun deleteWaypoint(selector: BaritoneWaypointSelector) =
        context.locked { context.deleteAdapterWaypoint(selector) }

    override fun executeCommand(command: String) = context.locked { context.executeAdapterCommand(command) }

    override fun completions(input: String, cursor: Int) = context.locked { context.adapterCompletions(input, cursor) }

    override fun lifecycle(event: BaritoneLifecycleEvent) = context.locked { context.applyAdapterLifecycle(event) }

    override fun clearAllKeys() = context.locked { context.clearAdapterKeys() }
}
