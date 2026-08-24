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
import baritone.api.event.events.PathEvent
import baritone.api.event.events.TickEvent
import baritone.api.event.events.type.EventState
import baritone.api.event.listener.AbstractGameEventListener
import baritone.api.pathing.path.IPathExecutor
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCapability
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseCause
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseController
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneProgress
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevisionClock
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoutePoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRouteSimplifier
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.features.baritone.core.BoundedBaritoneLogBuffer
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightPathObservation
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightPauseProcess
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeConfig
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeCoordinator
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeInput
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeSignal
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.FlightRuntimePosition
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.LiquidBounceFlyAutomationPort
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.MinecraftFlightPlannerPort
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.RuntimePathSegment
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.observeBaritoneFlightPath
import net.minecraft.client.Minecraft
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.sqrt

fun interface BaritoneConflictDetector {
    fun detect(): Collection<BaritonePauseCause>
}

/** Official Baritone 26.2 infrastructure adapter. No caller outside this package needs the third-party API. */
@Suppress("TooManyFunctions", "LongParameterList")
class BaritoneApiAdapter private constructor(
    private val baritone: IBaritone,
    conflictDetector: BaritoneConflictDetector,
    resumeDelayTicks: () -> Int,
    private val navigationMode: () -> BaritoneNavigationMode,
    private val flightRuntimeConfig: () -> BaritoneFlightRuntimeConfig,
    onAutomationStart: () -> Unit,
    gameDirectory: Path,
    private val externalMessageSink: BaritoneMessageSink,
    private val currentTimeMillis: () -> Long,
) : BaritoneFacade, AbstractGameEventListener {

    private val revisions = BaritoneRevisionClock()
    private val logBuffer = BoundedBaritoneLogBuffer()
    private val pauseController = BaritonePauseController(resumeDelayTicks)
    private val automationActivation = BaritoneAutomationActivation(onAutomationStart)
    private val routeSimplifier = BaritoneRouteSimplifier()
    private val conflictDetector = conflictDetector
    private val settingsBackend = OfficialBaritoneSettings(
        BaritoneAPI.getSettings(),
        BaritoneMessageSink(::acceptMessage),
    )
    private val settingsConfig = BaritoneSettingsConfig.install(
        settingsBackend,
        gameDirectory.resolve("baritone").resolve("settings.txt"),
    )
    private val taskDispatcher = BaritoneTaskDispatcher(
        baritone,
        settingsConfig,
        SchematicPathPolicy(gameDirectory.resolve("schematics")),
        gameDirectory.resolve("baritone"),
    )
    private val waypointAdapter = BaritoneWaypointAdapter(baritone)
    private val flightCoordinator = BaritoneFlightRuntimeCoordinator(
        fly = LiquidBounceFlyAutomationPort,
        planner = MinecraftFlightPlannerPort(
            world = { runCatching { baritone.playerContext.world() }.getOrNull() },
            player = { runCatching { baritone.playerContext.player() }.getOrNull() },
        ),
        config = flightRuntimeConfig,
    )
    private val pauseProcess = LiquidBouncePauseProcess(pauseController, ::pathingRelevant)
    private val flightPauseProcess = BaritoneFlightPauseProcess(flightCoordinator::ownsNativeMovement)
    private val lifecyclePolicy = BaritoneLifecyclePolicy(
        cancelEverything = { baritone.pathingBehavior.cancelEverything() },
        clearAllKeys = { baritone.inputOverrideHandler.clearAllKeys() },
        resetPause = pauseController::reset,
        resetAutomaticPause = pauseController::resetAutomatic,
        invalidateRoute = { invalidateRoute(forceRevision = true) },
    )

    @Volatile
    private var activeTask: BaritoneTaskRequest? = null
    private var observedPhase = BaritonePhase.IDLE
    private var lastFailure: BaritoneError? = null
    private var currentRoute = BaritoneRoute(BaritoneRevision.ZERO)
    private var walkingExecutor: IPathExecutor? = null
    private var walkingExecutorPosition = 0
    private var walkingFallbackObserved = false
    private var pathCancellationPending = false

    init {
        baritone.pathingControlManager.registerProcess(pauseProcess)
        baritone.pathingControlManager.registerProcess(flightPauseProcess)
        baritone.gameEventHandler.registerEventListener(this)
    }

    override fun capability() = BaritoneCapability.AVAILABLE

    @Synchronized
    override fun snapshot(): BaritoneSnapshot {
        val pauseState = pauseController.current()
        val navigation = navigationSnapshot()
        return BaritoneSnapshot(
            revision = revisions.next(),
            availability = BaritoneCapability.AVAILABLE,
            status = currentPhase(pauseState.cause),
            task = activeTask,
            etaSeconds = if (navigation.activeMode == BaritoneNavigationMode.FLY) {
                flightCoordinator.estimatedSeconds()
            } else {
                estimatedSeconds()
            },
            progress = if (navigation.activeMode == BaritoneNavigationMode.FLY) {
                flightCoordinator.progress()
            } else {
                progress()
            },
            pauseReason = pauseState.cause,
            settings = settings(),
            waypoints = waypoints(),
            logs = logBuffer.entries(),
            failure = lastFailure,
            navigation = navigation,
        )
    }

    @Synchronized
    override fun route(): BaritoneRoute = currentRoute

    @Synchronized
    override fun submitTask(task: BaritoneTaskRequest): BaritoneResult<BaritoneSnapshot> = execute("task") {
        requireWorld()
        automationActivation.afterSuccess { taskDispatcher.submit(task) }
        flightCoordinator.startTask(navigationMode())
        activeTask = task
        observedPhase = if (pauseController.current().paused) BaritonePhase.PAUSED else BaritonePhase.CALCULATING
        lastFailure = null
        refreshRoute()
        snapshot()
    }.alsoFailure(::rememberTaskFailure)

    @Synchronized
    override fun control(action: BaritoneControlAction): BaritoneResult<BaritoneSnapshot> = execute("action") {
        when (action) {
            BaritoneControlAction.PAUSE -> pauseController.pauseManually()
            BaritoneControlAction.RESUME -> pauseController.resumeManually()
            BaritoneControlAction.CANCEL -> cancelEverything()
        }
        snapshot()
    }

    @Synchronized
    override fun settings(): List<BaritoneSetting> = settingsConfig.settings().map(NativeBaritoneSetting::toCoreSetting)

    @Synchronized
    override fun setting(name: BaritoneSettingName): BaritoneSetting? =
        settingsConfig.setting(name.value)?.toCoreSetting()

    @Synchronized
    override fun updateSetting(
        name: BaritoneSettingName,
        value: BaritoneSettingValue,
    ): BaritoneResult<BaritoneSetting> = execute(name.value) {
        val previous = setting(name) ?: throw notFoundSetting(name)
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

    @Synchronized
    override fun resetSetting(name: BaritoneSettingName): BaritoneResult<BaritoneSetting> = execute(name.value) {
        settingsConfig.reset(name.value).getOrElse { throw notFoundSetting(name, it) }.toCoreSetting()
    }

    @Synchronized
    override fun resetSettings(): BaritoneResult<List<BaritoneSetting>> = execute {
        settingsConfig.resetAllSettings().map(NativeBaritoneSetting::toCoreSetting)
    }

    @Synchronized
    override fun deleteSetting(name: BaritoneSettingName): BaritoneResult<Unit> = execute(name.value) {
        settingsConfig.delete(name.value).getOrElse { throw notFoundSetting(name, it) }
    }

    @Synchronized
    override fun waypoints(): List<BaritoneWaypoint> = runCatching(waypointAdapter::waypoints).getOrDefault(emptyList())

    @Synchronized
    override fun addWaypoint(waypoint: BaritoneWaypointDraft): BaritoneResult<BaritoneWaypoint> = execute("waypoint") {
        waypointAdapter.add(waypoint)
    }

    @Synchronized
    override fun deleteWaypoint(selector: BaritoneWaypointSelector): BaritoneResult<Unit> = execute("waypoint") {
        waypointAdapter.delete(selector)
    }

    @Synchronized
    override fun executeCommand(command: String): BaritoneResult<BaritoneCommandOutput> {
        val normalized = command.trim().removePrefix("#").trim()
        if (normalized.isBlank()) return failure(BaritoneErrorCode.INVALID_FIELD, "Command cannot be blank", "command")
        executeSettingsCommand(normalized)?.let { return it }

        return execute("command", BaritoneErrorCode.COMMAND_FAILED) {
            requireWorld()
            val previousRevision = logBuffer.latestRevision()
            if (!automationActivation.accepted(baritone.commandManager.execute(normalized))) {
                throw BaritoneAdapterException(
                    BaritoneErrorCode.COMMAND_FAILED,
                    "Unknown or invalid Baritone command",
                    "command",
                )
            }
            if (pathingRelevant()) flightCoordinator.startTask(navigationMode())
            val output = logBuffer.entries().filter { it.revision > previousRevision }.map(BaritoneLogEntry::message)
            BaritoneCommandOutput(output.ifEmpty { listOf("Command executed.") })
        }
    }

    @Synchronized
    override fun completions(input: String, cursor: Int): BaritoneResult<List<String>> = execute("cursor") {
        if (cursor !in 0..input.length) {
            throw BaritoneAdapterException(BaritoneErrorCode.INVALID_FIELD, "Cursor is outside the input", "cursor")
        }
        val prefix = input.substring(0, cursor).trimStart().removePrefix("#")
        baritone.commandManager.tabComplete(prefix).limit(MAX_COMPLETIONS.toLong()).toList().distinct()
    }

    @Synchronized
    override fun lifecycle(event: BaritoneLifecycleEvent): BaritoneResult<Unit> = execute {
        if (event == BaritoneLifecycleEvent.DIMENSION_CHANGE) {
            flightCoordinator.dimensionChanged()
        } else {
            flightCoordinator.terminate()
        }
        lifecyclePolicy.apply(event)
        if (event != BaritoneLifecycleEvent.DIMENSION_CHANGE) {
            activeTask = null
            observedPhase = BaritonePhase.IDLE
            lastFailure = null
            pathCancellationPending = false
        }
    }

    @Synchronized
    override fun clearAllKeys(): BaritoneResult<Unit> = execute {
        baritone.inputOverrideHandler.clearAllKeys()
    }

    @Synchronized
    override fun onTick(event: TickEvent) {
        if (event.state != EventState.PRE) return
        if (worldAvailable() && observedPhase != BaritonePhase.ARRIVED && observedPhase != BaritonePhase.FAILED &&
            nativePathingRelevant() &&
            flightCoordinator.snapshot().phase == BaritoneNavigationPhase.IDLE
        ) {
            automationActivation.observedPathStart()
            flightCoordinator.startTask(navigationMode())
        }
        val causes = runCatching(conflictDetector::detect).getOrElse {
            appendLog(BaritoneLogLevel.ERROR, "Unable to evaluate Baritone conflicts: ${it.message.orEmpty()}")
            emptyList()
        }
        val pauseState = pauseController.tick(causes)
        if (worldAvailable()) {
            val userInput = pauseState.cause?.reason == BaritonePauseReason.USER_INPUT
            val runtimeResult = flightCoordinator.tick(BaritoneFlightRuntimeInput(
                playerPosition = baritone.playerContext.player().position().let {
                    FlightRuntimePosition(it.x, it.y, it.z)
                },
                path = observeFlightPath(),
                userInput = userInput,
                paused = pauseState.paused && !userInput,
                completedWalkPathBlocks = completedWalkingPathBlocks(),
            ))
            handleFlightRuntimeSignal(runtimeResult.signal)
        }
        if (pathCancellationPending && !nativePathingRelevant()) {
            flightCoordinator.terminate()
            activeTask = null
            observedPhase = BaritonePhase.IDLE
            pathCancellationPending = false
        }
        if (event.count % ROUTE_UPDATE_INTERVAL_TICKS == 0) refreshRoute()
    }

    @Synchronized
    override fun onPathEvent(event: PathEvent) {
        if (event in PATH_START_EVENTS && flightCoordinator.snapshot().phase == BaritoneNavigationPhase.IDLE) {
            automationActivation.observedPathStart()
            flightCoordinator.startTask(navigationMode())
        }
        if (event in PATH_START_EVENTS) pathCancellationPending = false
        when (event) {
            PathEvent.CALC_STARTED, PathEvent.NEXT_SEGMENT_CALC_STARTED -> observedPhase = BaritonePhase.CALCULATING
            PathEvent.CALC_FINISHED_NOW_EXECUTING,
            PathEvent.NEXT_SEGMENT_CALC_FINISHED,
            PathEvent.CONTINUING_ONTO_PLANNED_NEXT,
            PathEvent.SPLICING_ONTO_NEXT_EARLY -> observedPhase = BaritonePhase.PATHING
            PathEvent.AT_GOAL -> {
                observedPhase = BaritonePhase.ARRIVED
                flightCoordinator.terminate()
            }
            PathEvent.CALC_FAILED, PathEvent.NEXT_CALC_FAILED -> {
                flightCoordinator.terminate()
                observedPhase = BaritonePhase.FAILED
                lastFailure = BaritoneError(BaritoneErrorCode.INVALID_STATE, "Baritone could not calculate a path")
            }
            PathEvent.CANCELED -> {
                if (preservesResultAfterCancellation(observedPhase)) {
                    pathCancellationPending = false
                } else {
                    pathCancellationPending = true
                    if (!nativePathingRelevant()) {
                        flightCoordinator.terminate()
                        activeTask = null
                        observedPhase = BaritonePhase.IDLE
                        pathCancellationPending = false
                    }
                }
            }
            PathEvent.PATH_FINISHED_NEXT_STILL_CALCULATING -> observedPhase = BaritonePhase.CALCULATING
            PathEvent.DISCARD_NEXT -> Unit
        }
        refreshRoute()
    }

    private fun currentPhase(pauseCause: BaritonePauseCause?): BaritonePhase {
        if (!worldAvailable()) return BaritonePhase.NO_WORLD
        if (pauseCause != null && (pauseCause.reason == BaritonePauseReason.MANUAL || pathingRelevant())) {
            return BaritonePhase.PAUSED
        }
        if (lastFailure != null && observedPhase == BaritonePhase.FAILED) return BaritonePhase.FAILED
        val behavior = baritone.pathingBehavior
        if (behavior.inProgress.isPresent) return BaritonePhase.CALCULATING
        if (behavior.isPathing) return BaritonePhase.PATHING
        navigationBaritonePhase(flightCoordinator.snapshot().phase)?.let { return it }
        return observedPhase.takeIf { activeTask != null } ?: BaritonePhase.IDLE
    }

    private fun estimatedSeconds(): Long? = baritone.pathingBehavior.estimatedTicksToGoal()
        .filter { it.isFinite() && it >= 0.0 }
        .map { ceil(it / TICKS_PER_SECOND).toLong() }
        .orElse(null)

    private fun progress(): BaritoneProgress? {
        val executor = baritone.pathingBehavior.current ?: return when (observedPhase) {
            BaritonePhase.ARRIVED -> BaritoneProgress(1.0, 0.0)
            else -> null
        }
        val path = executor.path
        val positions = path.positions()
        if (positions.isEmpty()) return null
        val index = executor.position.coerceIn(0, positions.lastIndex)
        val fraction = if (positions.size == 1) 1.0 else index.toDouble() / positions.lastIndex
        var remaining = 0.0
        for (position in index until positions.lastIndex) {
            val first = positions[position]
            val second = positions[position + 1]
            val x = (second.x - first.x).toDouble()
            val y = (second.y - first.y).toDouble()
            val z = (second.z - first.z).toDouble()
            remaining += sqrt(x * x + y * y + z * z)
        }
        return BaritoneProgress(fraction, remaining, path.numNodesConsidered.toLong())
    }

    private fun refreshRoute() {
        val points = flightCoordinator.route().mapTo(ArrayList()) { position ->
            BaritoneRoutePoint(position.x, position.y, position.z)
        }
        if (points.isEmpty()) {
            appendPath(points, baritone.pathingBehavior.current)
            appendPath(points, baritone.pathingBehavior.next)
        }
        if (points.isEmpty()) {
            baritone.elytraProcess.path.forEach { position ->
                points += BaritoneRoutePoint(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
            }
        }
        val simplified = routeSimplifier.simplify(points)
        if (simplified == currentRoute.points) return
        currentRoute = BaritoneRoute(revisions.next(), simplified)
    }

    private fun appendPath(points: MutableList<BaritoneRoutePoint>, executor: IPathExecutor?) {
        executor ?: return
        val positions = executor.path.positions()
        val start = executor.position.coerceIn(0, positions.size)
        for (position in positions.drop(start)) {
            val point = BaritoneRoutePoint(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
            if (points.lastOrNull() != point) points += point
        }
    }

    private fun invalidateRoute(forceRevision: Boolean) {
        if (!forceRevision && currentRoute.points.isEmpty()) return
        currentRoute = BaritoneRoute(revisions.next())
    }

    private fun cancelEverything() {
        flightCoordinator.terminate()
        baritone.pathingBehavior.cancelEverything()
        baritone.inputOverrideHandler.clearAllKeys()
        pauseController.reset()
        activeTask = null
        observedPhase = BaritonePhase.IDLE
        lastFailure = null
        pathCancellationPending = false
        invalidateRoute(forceRevision = true)
    }

    private fun worldAvailable(): Boolean =
        baritone.playerContext.world() != null && baritone.playerContext.player() != null

    private fun requireWorld() {
        if (!worldAvailable()) {
            throw BaritoneAdapterException(BaritoneErrorCode.INVALID_STATE, "No Minecraft world is loaded")
        }
    }

    private fun pathingRelevant(): Boolean {
        val taskPending = activeTask != null && observedPhase !in TERMINAL_PHASES
        return taskPending || nativePathingRelevant() || flightCoordinator.snapshot().phase !in setOf(
            BaritoneNavigationPhase.IDLE,
            BaritoneNavigationPhase.WAITING_FOR_PATH,
        )
    }

    private fun nativePathingRelevant(): Boolean {
        val behavior = baritone.pathingBehavior
        val elytra = baritone.elytraProcess
        val externalProcess = baritone.pathingControlManager.mostRecentInControl()
            .filter { process -> process !== pauseProcess && process !== flightPauseProcess }
            .filter { process -> runCatching(process::isActive).getOrDefault(false) }
            .isPresent
        return behavior.hasPath() || behavior.inProgress.isPresent || behavior.isPathing ||
            elytra.isActive || elytra.currentDestination() != null || externalProcess
    }

    private fun navigationSnapshot() = flightCoordinator.snapshot().let { navigation ->
        val hasRuntimeState = navigation.phase != BaritoneNavigationPhase.IDLE ||
            navigation.activeMode != null || navigation.detail != null
        if (hasRuntimeState) {
            navigation
        } else {
            navigation.copy(
                requestedMode = navigationMode(),
                restartsRemaining = flightRuntimeConfig().maxRestarts,
            )
        }
    }

    private fun observeFlightPath(): BaritoneFlightPathObservation = observeBaritoneFlightPath(
        current = baritone.pathingBehavior.current?.toRuntimeSegment(),
        next = baritone.pathingBehavior.next?.toRuntimeSegment(),
        elytraDestination = baritone.elytraProcess.currentDestination()?.let { destination ->
            FlightRuntimePosition(destination.x + 0.5, destination.y.toDouble(), destination.z + 0.5)
        },
    )

    private fun IPathExecutor.toRuntimeSegment(): RuntimePathSegment = RuntimePathSegment(
        positions = path.positions().map { position ->
            FlightRuntimePosition(position.x + 0.5, position.y.toDouble(), position.z + 0.5)
        },
        currentIndex = position,
    )

    /** Converts the upstream executor's cumulative position into the policy's per-tick positive delta. */
    private fun completedWalkingPathBlocks(): Int {
        val fallback = flightCoordinator.snapshot().phase == BaritoneNavigationPhase.WALK_FALLBACK
        val current = baritone.pathingBehavior.current
        if (!fallback || current == null) {
            walkingFallbackObserved = false
            walkingExecutor = current
            walkingExecutorPosition = current?.position ?: 0
            return 0
        }
        if (!walkingFallbackObserved || walkingExecutor !== current) {
            walkingFallbackObserved = true
            walkingExecutor = current
            walkingExecutorPosition = current.position
            return 0
        }
        val delta = (current.position - walkingExecutorPosition).coerceAtLeast(0)
        walkingExecutorPosition = current.position
        return delta
    }

    private fun handleFlightRuntimeSignal(signal: BaritoneFlightRuntimeSignal?) {
        signal ?: return
        pathCancellationPending = false
        when (signal) {
            BaritoneFlightRuntimeSignal.Arrived -> {
                flightCoordinator.terminate()
                observedPhase = BaritonePhase.ARRIVED
                lastFailure = null
            }
            is BaritoneFlightRuntimeSignal.FailTask -> rememberFlightFailure(signal.detail)
            is BaritoneFlightRuntimeSignal.CancelTask -> rememberFlightFailure(signal.detail)
        }
        baritone.pathingBehavior.cancelEverything()
        baritone.inputOverrideHandler.clearAllKeys()
        invalidateRoute(forceRevision = true)
    }

    private fun rememberFlightFailure(detail: String) {
        observedPhase = BaritonePhase.FAILED
        lastFailure = BaritoneError(BaritoneErrorCode.INVALID_STATE, detail)
    }

    private fun acceptMessage(message: BaritoneAdapterMessage) {
        when (message) {
            is BaritoneAdapterMessage.Log -> appendLog(BaritoneLogLevel.INFO, message.message)
            is BaritoneAdapterMessage.Notification -> appendLog(
                if (message.error) BaritoneLogLevel.ERROR else BaritoneLogLevel.INFO,
                message.message,
            )
            is BaritoneAdapterMessage.Toast -> appendLog(
                BaritoneLogLevel.INFO,
                listOf(message.title, message.message).filter(String::isNotBlank).joinToString(": "),
            )
        }
        runCatching { externalMessageSink.accept(message) }
    }

    @Synchronized
    private fun appendLog(level: BaritoneLogLevel, message: String) {
        if (message.isBlank()) return
        logBuffer.append(BaritoneLogEntry(revisions.next(), level, message, currentTimeMillis()))
    }

    private fun executeSettingsCommand(command: String): BaritoneResult<BaritoneCommandOutput>? {
        val parts = command.split(Regex("\\s+"), limit = 3)
        if (parts.first().lowercase() !in SETTINGS_COMMANDS) return null
        val firstArgument = parts.getOrNull(1)?.lowercase() ?: "list"
        return when (firstArgument) {
            "list", "all", "modified", "mod", "m" -> {
                val all = settings()
                val selected = if (firstArgument in setOf("modified", "mod", "m")) {
                    all.filter { it.value != it.defaultValue }
                } else {
                    all
                }
                val messages = selected.map { "${it.name.value} ${it.value.toUpstreamString()}" }
                BaritoneResult.Success(BaritoneCommandOutput(messages))
            }
            "save", "s" -> {
                ConfigSystem.store(settingsConfig)
                BaritoneResult.Success(BaritoneCommandOutput(listOf("Settings saved to baritone.json")))
            }
            "load", "ld" -> {
                ConfigSystem.load(settingsConfig)
                BaritoneResult.Success(BaritoneCommandOutput(listOf("Settings loaded from baritone.json")))
            }
            "reset" -> executeSettingsReset(parts.getOrNull(2))
            "toggle" -> executeSettingsToggle(parts.getOrNull(2))
            else -> executeSettingsReadOrWrite(parts[1], parts.getOrNull(2))
        }
    }

    private fun executeSettingsReset(name: String?): BaritoneResult<BaritoneCommandOutput> {
        if (name == null) return failure(BaritoneErrorCode.INVALID_FIELD, "Specify a setting or 'all'", "command")
        if (name.equals("all", ignoreCase = true)) {
            return resetSettings().mapSuccess { BaritoneCommandOutput(listOf("All Baritone settings were reset")) }
        }
        val settingName = BaritoneSettingName(name)
        return resetSetting(settingName).mapSuccess {
            BaritoneCommandOutput(listOf("Reset ${it.name.value} to ${it.value.toUpstreamString()}"))
        }
    }

    private fun executeSettingsToggle(name: String?): BaritoneResult<BaritoneCommandOutput> {
        if (name == null) return failure(BaritoneErrorCode.INVALID_FIELD, "Specify a Boolean setting", "command")
        val settingName = BaritoneSettingName(name)
        val setting = setting(settingName)
            ?: return failure(BaritoneErrorCode.NOT_FOUND, "Unknown Baritone setting: $name", name)
        if (setting.type != BaritoneSettingType.BOOLEAN) {
            return failure(BaritoneErrorCode.INVALID_FIELD, "Setting '$name' is not Boolean", name)
        }
        val toggled = !(setting.value as BaritoneSettingValue.BooleanValue).value
        return updateSetting(settingName, BaritoneSettingValue.BooleanValue(toggled)).mapSuccess {
            BaritoneCommandOutput(listOf("${it.name.value} ${it.value.toUpstreamString()}"))
        }
    }

    private fun executeSettingsReadOrWrite(name: String, rawValue: String?): BaritoneResult<BaritoneCommandOutput> {
        val settingName = BaritoneSettingName(name)
        if (rawValue == null) {
            val setting = setting(settingName)
                ?: return failure(BaritoneErrorCode.NOT_FOUND, "Unknown Baritone setting: $name", name)
            return BaritoneResult.Success(
                BaritoneCommandOutput(listOf("${setting.name.value} ${setting.value.toUpstreamString()}"))
            )
        }
        return execute(name) {
            settingsConfig.update(name, rawValue).getOrThrow().toCoreSetting()
        }.mapSuccess { BaritoneCommandOutput(listOf("${it.name.value} ${it.value.toUpstreamString()}")) }
    }

    private fun rememberTaskFailure(failure: BaritoneResult.Failure) {
        observedPhase = BaritonePhase.FAILED
        lastFailure = failure.error
    }

    private fun notFoundSetting(name: BaritoneSettingName, cause: Throwable? = null) = BaritoneAdapterException(
        BaritoneErrorCode.NOT_FOUND,
        cause?.message ?: "Unknown Baritone setting: ${name.value}",
        name.value,
        cause,
    )

    private inline fun <T> execute(
        field: String? = null,
        fallbackCode: BaritoneErrorCode = BaritoneErrorCode.INTERNAL_ERROR,
        operation: () -> T,
    ): BaritoneResult<T> = try {
        BaritoneResult.Success(operation())
    } catch (error: BaritoneAdapterException) {
        failure(error.code, error.message, error.field ?: field)
    } catch (error: IllegalArgumentException) {
        failure(BaritoneErrorCode.INVALID_FIELD, error.message.orEmpty().ifBlank { "Invalid value" }, field)
    } catch (error: IllegalStateException) {
        failure(BaritoneErrorCode.INVALID_STATE, error.message.orEmpty().ifBlank { "Invalid state" }, field)
    } catch (error: Throwable) {
        appendLog(BaritoneLogLevel.ERROR, "Baritone operation failed: ${error.message.orEmpty()}")
        failure(fallbackCode, error.message.orEmpty().ifBlank { "Baritone operation failed" }, field)
    }

    private fun <T> failure(code: BaritoneErrorCode, message: String, field: String? = null): BaritoneResult<T> =
        BaritoneResult.Failure(BaritoneError(code, message, field))

    companion object {
        private const val TICKS_PER_SECOND = 20.0
        private const val ROUTE_UPDATE_INTERVAL_TICKS = 4
        private const val MAX_COMPLETIONS = 100
        private val SETTINGS_COMMANDS = setOf("set", "setting", "settings")
        private val PATH_START_EVENTS = setOf(PathEvent.CALC_STARTED, PathEvent.NEXT_SEGMENT_CALC_STARTED)
        private val TERMINAL_PHASES = setOf(BaritonePhase.IDLE, BaritonePhase.ARRIVED, BaritonePhase.FAILED)

        @JvmStatic
        @JvmOverloads
        fun create(
            conflictDetector: BaritoneConflictDetector = BaritoneConflictDetector { emptyList() },
            resumeDelayTicks: () -> Int = { BaritonePauseController.DEFAULT_RESUME_DELAY_TICKS },
            navigationMode: () -> BaritoneNavigationMode = { BaritoneNavigationMode.FLY },
            flightRuntimeConfig: () -> BaritoneFlightRuntimeConfig = { BaritoneFlightRuntimeConfig() },
            onAutomationStart: () -> Unit = {},
            messageSink: BaritoneMessageSink = BaritoneMessageSink.NONE,
            gameDirectory: Path = Minecraft.getInstance().gameDirectory.toPath(),
            currentTimeMillis: () -> Long = System::currentTimeMillis,
        ): BaritoneFacade {
            val result = runCatching {
                Class.forName("baritone.api.BaritoneAPI", false, BaritoneApiAdapter::class.java.classLoader)
                BaritoneAPI.getProvider().primaryBaritone
            }
            return result.fold(
                onSuccess = {
                    BaritoneApiAdapter(
                        it,
                        conflictDetector,
                        resumeDelayTicks,
                        navigationMode,
                        flightRuntimeConfig,
                        onAutomationStart,
                        gameDirectory,
                        messageSink,
                        currentTimeMillis,
                    )
                },
                onFailure = {
                    UnavailableBaritoneFacade("Baritone API failed to initialize: ${it.message.orEmpty()}")
                },
            )
        }
    }
}

internal fun navigationBaritonePhase(phase: BaritoneNavigationPhase): BaritonePhase? = when (phase) {
    BaritoneNavigationPhase.WAITING_FOR_PATH,
    BaritoneNavigationPhase.PLANNING,
    BaritoneNavigationPhase.ARMING -> BaritonePhase.CALCULATING
    BaritoneNavigationPhase.FLYING -> BaritonePhase.PATHING
    BaritoneNavigationPhase.WAITING_FOR_USER -> BaritonePhase.PAUSED
    BaritoneNavigationPhase.IDLE,
    BaritoneNavigationPhase.WALK_FALLBACK -> null
}

internal fun preservesResultAfterCancellation(phase: BaritonePhase): Boolean =
    phase == BaritonePhase.FAILED || phase == BaritonePhase.ARRIVED

private inline fun <T, R> BaritoneResult<T>.mapSuccess(transform: (T) -> R): BaritoneResult<R> = when (this) {
    is BaritoneResult.Success -> BaritoneResult.Success(transform(value))
    is BaritoneResult.Failure -> this
}

private inline fun <T> BaritoneResult<T>.alsoFailure(action: (BaritoneResult.Failure) -> Unit): BaritoneResult<T> =
    also { if (it is BaritoneResult.Failure) action(it) }
