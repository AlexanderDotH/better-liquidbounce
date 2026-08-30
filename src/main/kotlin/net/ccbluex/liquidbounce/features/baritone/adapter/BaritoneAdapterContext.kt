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

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.pathing.path.IPathExecutor
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseController
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevisionClock
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRouteSimplifier
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BoundedBaritoneLogBuffer
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightPauseProcess
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeConfig
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeCoordinator
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.MinecraftFlightPlannerPort
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.integration.LiquidBounceFlyAutomationPort
import java.nio.file.Path

@Suppress("LongParameterList")
internal class BaritoneAdapterContext(
    val baritone: IBaritone,
    val conflictDetector: BaritoneConflictDetector,
    resumeDelayTicks: () -> Int,
    val navigationMode: () -> BaritoneNavigationMode,
    val flightRuntimeConfig: () -> BaritoneFlightRuntimeConfig,
    onAutomationStart: () -> Unit,
    gameDirectory: Path,
    val externalMessageSink: BaritoneMessageSink,
    val currentTimeMillis: () -> Long,
) : BaritoneSettingsCommandAccess {

    val lock = Any()
    val revisions = BaritoneRevisionClock()
    val logBuffer = BoundedBaritoneLogBuffer()
    val pauseController = BaritonePauseController(resumeDelayTicks)
    val automationActivation = BaritoneAutomationActivation(onAutomationStart)
    val routeSimplifier = BaritoneRouteSimplifier()
    private val settingsBackend = OfficialBaritoneSettings(
        BaritoneAPI.getSettings(),
        BaritoneMessageSink { message -> acceptAdapterMessage(message) },
    )
    val settingsConfig = BaritoneSettingsConfig.install(
        settingsBackend,
        gameDirectory.resolve("baritone").resolve("settings.txt"),
    )
    val settingsCommandDispatcher = BaritoneSettingsCommandDispatcher(
        access = this,
        persistence = LiquidBounceBaritoneSettingsPersistence(settingsConfig),
    )
    val taskDispatcher = BaritoneTaskDispatcher(
        baritone,
        settingsConfig,
        SchematicPathPolicy(gameDirectory.resolve("schematics")),
        gameDirectory.resolve("baritone"),
    )
    val waypointAdapter = BaritoneWaypointAdapter(baritone)
    val flightCoordinator = BaritoneFlightRuntimeCoordinator(
        fly = LiquidBounceFlyAutomationPort,
        planner = MinecraftFlightPlannerPort(
            world = { runCatching { baritone.playerContext.world() }.getOrNull() },
            player = { runCatching { baritone.playerContext.player() }.getOrNull() },
        ),
        config = flightRuntimeConfig,
    )
    val pauseProcess = LiquidBouncePauseProcess(pauseController) { pathingRelevant() }
    val flightPauseProcess = BaritoneFlightPauseProcess(flightCoordinator::ownsNativeMovement)
    val lifecyclePolicy = BaritoneLifecyclePolicy(
        cancelEverything = { baritone.pathingBehavior.cancelEverything() },
        clearAllKeys = { baritone.inputOverrideHandler.clearAllKeys() },
        resetPause = pauseController::reset,
        resetAutomaticPause = pauseController::resetAutomatic,
        invalidateRoute = { invalidateAdapterRoute(forceRevision = true) },
    )
    val facade: BaritoneFacade = BaritoneAdapterFacade(this)

    @Volatile
    var activeTask: BaritoneTaskRequest? = null
    var observedPhase = BaritonePhase.IDLE
    var lastFailure: BaritoneError? = null
    var currentRoute = BaritoneRoute(BaritoneRevision.ZERO)
    var walkingExecutor: IPathExecutor? = null
    var walkingExecutorPosition = 0
    var walkingFallbackObserved = false
    var pathCancellationPending = false

    fun <T> locked(operation: () -> T): T = synchronized(lock, operation)

    override fun settings(): List<BaritoneSetting> = adapterSettings()
    override fun setting(name: BaritoneSettingName): BaritoneSetting? = adapterSetting(name)
    override fun updateSetting(name: BaritoneSettingName, value: BaritoneSettingValue) =
        updateAdapterSetting(name, value)
    override fun resetSetting(name: BaritoneSettingName) = resetAdapterSetting(name)
    override fun resetSettings() = resetAdapterSettings()
    override fun writeSetting(name: String, rawValue: String) = executeAdapterOperation(name) {
        settingsConfig.update(name, rawValue).getOrThrow().toCoreSetting()
    }
}
