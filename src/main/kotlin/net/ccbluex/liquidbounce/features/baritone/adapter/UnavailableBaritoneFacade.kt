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
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector

@Suppress("TooManyFunctions")
internal class UnavailableBaritoneFacade(reason: String) : BaritoneFacade {

    private val error = BaritoneError(
        BaritoneErrorCode.UNAVAILABLE,
        reason.ifBlank { "Baritone is unavailable" },
    )
    private val unavailable = BaritoneResult.Failure(error)

    override fun capability() = BaritoneCapability.UNAVAILABLE

    override fun snapshot() = BaritoneSnapshot(
        revision = BaritoneRevision.ZERO,
        availability = BaritoneCapability.UNAVAILABLE,
        status = BaritonePhase.UNAVAILABLE,
        failure = error,
    )

    override fun route() = BaritoneRoute(BaritoneRevision.ZERO)
    override fun settings() = emptyList<BaritoneSetting>()
    override fun setting(name: BaritoneSettingName): BaritoneSetting? = null
    override fun waypoints() = emptyList<BaritoneWaypoint>()

    override fun submitTask(task: BaritoneTaskRequest) = unavailable
    override fun control(action: BaritoneControlAction) = unavailable
    override fun updateSetting(name: BaritoneSettingName, value: BaritoneSettingValue) = unavailable
    override fun resetSetting(name: BaritoneSettingName) = unavailable
    override fun resetSettings() = unavailable
    override fun deleteSetting(name: BaritoneSettingName) = unavailable
    override fun addWaypoint(waypoint: BaritoneWaypointDraft) = unavailable
    override fun deleteWaypoint(selector: BaritoneWaypointSelector) = unavailable
    override fun executeCommand(command: String): BaritoneResult<BaritoneCommandOutput> = unavailable
    override fun completions(input: String, cursor: Int): BaritoneResult<List<String>> = unavailable
    override fun lifecycle(event: BaritoneLifecycleEvent) = unavailable
    override fun clearAllKeys() = unavailable
}
