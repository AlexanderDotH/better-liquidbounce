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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DeathEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.utils.MutableVertexList
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import java.util.Locale

/**
 * Extended server-side interaction configuration attached below Reach.
 *
 * Runtime orchestration is deliberately kept in focused collaborators. This group only owns the
 * public schema and captures immutable settings before an interaction session begins.
 */
class ReachInteractableFeature(parent: EventListener) : ToggleableValueGroup(
    parent = parent,
    name = "Interactable",
    enabled = false,
) {

    private val runtimeDelegate = lazy(::MinecraftReachInteractableRuntime)
    private val runtime by runtimeDelegate
    private var reportedStatus: InteractableRuntimeStatus? = null

    private val maxRange by float("MaxRange", 256f, 8f..512f, "blocks")
    private val interactionRange by float("InteractionRange", 4.5f, 3f..6f, "blocks")
    private val filter by enumChoice("Filter", Filter.BLACKLIST)
    private val blocks by blocks("Blocks", blockSortedSetOf())
    private val containerVehicles by boolean("ContainerVehicles", true)

    internal val routing = tree(InteractableRoutingConfiguration())
    internal val surfaceFallback = tree(InteractableSurfaceFallbackConfiguration(this))

    private val openRetries by int("OpenRetries", 2, 0..10, "retries")
    private val openTimeout by int("OpenTimeout", 20, 1..200, "ticks")
    private val routeTimeout by int("RouteTimeout", 400, 20..2000, "ticks")
    private val holdTimeout by int("HoldTimeout", 0, 0..72000, "ticks")

    init {
        if (parent is ClientModule && parent.name == "Reach") attach(this)
    }

    override val running: Boolean
        get() = super.running || runtimeDelegate.isInitialized() && runtime.active

    override fun onDisabled() {
        if (runtimeDelegate.isInitialized()) runtime.abort(InteractableSessionCause.DISABLE)
        super.onDisabled()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        runtime.tick()
        reportRuntimeStatus()
    }

    @Suppress("unused")
    private val finalPacketHandler = handler<PacketEvent>(priority = Short.MIN_VALUE) { event ->
        if (!runtimeDelegate.isInitialized() || !runtime.active) return@handler
        runtime.onContainerPacket(event)
        runtime.rewriteOrConfirmOutgoing(event)
    }

    @Suppress("unused")
    private val inputHandler = handler<MovementInputEvent>(priority = FINAL_DECISION) { event ->
        if (!runtimeDelegate.isInitialized() || !runtime.suppressLocalMovement()) return@handler
        event.directionalInput = DirectionalInput.NONE
        event.jump = false
        event.sneak = false
    }

    @Suppress("unused")
    private val sprintHandler = handler<SprintEvent>(priority = FINAL_DECISION) { event ->
        if (runtimeDelegate.isInitialized() && runtime.suppressLocalMovement()) event.sprint = false
    }

    @Suppress("unused")
    private val moveHandler = handler<PlayerMoveEvent>(priority = FINAL_DECISION) { event ->
        if (!runtimeDelegate.isInitialized() || !runtime.suppressLocalMovement()) return@handler
        event.movement = Vec3.ZERO
        player.deltaMovement = Vec3.ZERO
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent> { event ->
        if (runtimeDelegate.isInitialized() && runtime.active) runtime.onScreen(event.screen)
    }

    @Suppress("unused")
    private val worldHandler = handler<WorldChangeEvent> {
        if (runtimeDelegate.isInitialized() && runtime.active) {
            runtime.hardReset(InteractableSessionCause.WORLD_CHANGE)
        }
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        if (runtimeDelegate.isInitialized() && runtime.active) {
            runtime.hardReset(InteractableSessionCause.DISCONNECT)
        }
    }

    @Suppress("unused")
    private val deathHandler = handler<DeathEvent> {
        if (runtimeDelegate.isInitialized() && runtime.active) {
            runtime.hardReset(InteractableSessionCause.DEATH)
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val snapshot = runtime.takeIf { routing.capture().renderPath }?.renderSnapshot
            as? InteractableRenderSnapshot.Route ?: return@handler
        event.renderEnvironment {
            snapshot.route.paths.forEach { path ->
                drawLineStrip(
                    Color4b.WHITE.argb,
                    MutableVertexList(path.points.size).addAllRelativeToCamera(path.points, camera) { it },
                )
            }
            snapshot.route.verticalClips.forEach { clip ->
                drawLineStrip(
                    Color4b.BLUE.argb,
                    MutableVertexList(2).addAllRelativeToCamera(listOf(clip.from, clip.to), camera) { it },
                )
            }
        }
    }

    internal val runtimeStatus: InteractableRuntimeStatus?
        get() = runtimeDelegate.takeIf { it.isInitialized() }?.value?.status

    private fun reportRuntimeStatus() {
        val status = runtime.status
        if (status == null || status == reportedStatus) return
        reportedStatus = status
        when (status) {
            is InteractableRuntimeStatus.Failure -> notification(
                "Reach Interactable",
                failureMessage(status.reason),
                NotificationEvent.Severity.ERROR,
            )
            is InteractableRuntimeStatus.RecoveryStalled -> notification(
                "Reach Interactable",
                ModuleReach.message(
                    "interactable.recoveryStalled",
                    ModuleReach.message("interactable.cause.${status.cause.name.toFailureKey()}"),
                ),
                NotificationEvent.Severity.ERROR,
            )
            is InteractableRuntimeStatus.Recovery -> notification(
                "Reach Interactable",
                ModuleReach.message(
                    "interactable.recovering",
                    ModuleReach.message("interactable.cause.${status.cause.name.toFailureKey()}"),
                ),
                NotificationEvent.Severity.ERROR,
            )
            is InteractableRuntimeStatus.Terminated -> notification(
                "Reach Interactable",
                ModuleReach.message(
                    "interactable.terminated",
                    ModuleReach.message("interactable.cause.${status.cause.name.toFailureKey()}"),
                ),
                NotificationEvent.Severity.ERROR,
            )
            is InteractableRuntimeStatus.Resynchronized -> notification(
                "Reach Interactable",
                ModuleReach.message(
                    "interactable.resynchronized",
                    status.position.x.formatCoordinate(),
                    status.position.y.formatCoordinate(),
                    status.position.z.formatCoordinate(),
                ),
                NotificationEvent.Severity.ERROR,
            )
            is InteractableRuntimeStatus.State -> Unit
        }
    }

    private fun failureMessage(reason: String) = reason.openAttemptNumber()?.let { attempt ->
        ModuleReach.message("interactable.failure.openAttempt", attempt)
    } ?: ModuleReach.message("interactable.failure.${reason.toFailureKey()}")

    internal fun captureSettings(): InteractableSettingsSnapshot = InteractableSettingsSnapshot(
        maxRange = maxRange.toDouble(),
        interactionRange = interactionRange.toDouble(),
        filter = InteractableBlockFilter(filter, blocks.toSet()),
        containerVehicles = containerVehicles,
        routing = routing.capture(),
        surfaceFallback = surfaceFallback.capture(),
        openRetries = openRetries,
        openTimeoutTicks = openTimeout,
        routeTimeoutTicks = routeTimeout,
        holdTimeoutTicks = holdTimeout,
    )

    private fun claimUseFromHook(): Boolean {
        if (!super.running) return false
        return runtime.claimUse(captureSettings()).also { reportRuntimeStatus() }
    }

    private fun beforeCorrectionFromHook(packet: ClientboundPlayerPositionPacket, player: Player) {
        if (runtimeDelegate.isInitialized()) runtime.beforeCorrection(packet, player)
    }

    private fun afterCorrectionFromHook(packet: ClientboundPlayerPositionPacket, player: Player) {
        if (runtimeDelegate.isInitialized()) {
            runtime.afterCorrection(packet, player)
            reportRuntimeStatus()
        }
    }

    companion object {
        @Volatile
        private var attached: ReachInteractableFeature? = null

        private fun attach(feature: ReachInteractableFeature) {
            attached = feature
        }

        @JvmStatic
        fun claimUse(): Boolean = attached?.claimUseFromHook() == true

        @JvmStatic
        fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            attached?.beforeCorrectionFromHook(packet, player)
        }

        @JvmStatic
        fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
            attached?.afterCorrectionFromHook(packet, player)
        }
    }
}

private fun String.openAttemptNumber(): Int? = takeIf {
    startsWith("OPEN_ATTEMPT_") && endsWith("_FAILED")
}?.removePrefix("OPEN_ATTEMPT_")?.removeSuffix("_FAILED")?.toIntOrNull()

private fun String.toFailureKey(): String = lowercase(Locale.ROOT)
    .split('_')
    .let { words ->
        words.first() + words.drop(1).joinToString("") { word ->
            word.replaceFirstChar { character -> character.uppercaseChar() }
        }
    }

private fun Double.formatCoordinate(): String = String.format(Locale.ROOT, "%.2f", this)

internal class InteractableRoutingConfiguration : ValueGroup("Routing") {
    private val maxCost by int("MaxCost", 4096, 256..16384)
    private val diagonal by boolean("Diagonal", true)
    private val lineOfSightShortcuts by boolean("LineOfSightShortcuts", true)
    private val stepDistance by float("StepDistance", 9.5f, 1f..20f, "blocks")
    private val stepDelay by int("StepDelay", 0, 0..20, "ticks")
    private val nodesPerTick by int("NodesPerTick", 750, 50..5000, "nodes")
    private val renderPath by boolean("RenderPath", true)

    fun capture() = InteractableRoutingSettings(
        maxCost = maxCost,
        diagonal = diagonal,
        lineOfSightShortcuts = lineOfSightShortcuts,
        stepDistance = stepDistance.toDouble(),
        stepDelayTicks = stepDelay,
        nodesPerTick = nodesPerTick,
        renderPath = renderPath,
    )
}

internal class InteractableSurfaceFallbackConfiguration(parent: EventListener) : ToggleableValueGroup(
    parent = parent,
    name = "SurfaceFallback",
    enabled = true,
) {
    private val maxRise by int("MaxRise", 128, 1..384, "blocks")
    private val horizontalSearch by int("HorizontalSearch", 48, 1..128, "blocks")
    private val doNotClipAroundBedrock by boolean("DoNotClipAroundBedrock", true)
    private val transportConfiguration = InteractableVClipConfiguration(this)
    private val transport = tree(transportConfiguration.choice)

    fun capture() = InteractableSurfaceFallbackSettings(
        enabled = enabled,
        maxRise = maxRise,
        horizontalSearch = horizontalSearch,
        doNotClipAroundBedrock = doNotClipAroundBedrock,
        transport = transportConfiguration.capture(),
    )
}

private class InteractableVClipConfiguration(parent: EventListener) {
    lateinit var vanilla: Vanilla
        private set
    lateinit var folia: Folia
        private set

    val choice = ModeValueGroup<Choice>(parent, "VClip", { 0 }) { choiceParent ->
        arrayOf(
            Vanilla(choiceParent).also { vanilla = it },
            Folia(choiceParent).also { folia = it },
        )
    }

    fun capture(): InteractableVClipSettings = when (choice.activeMode) {
        vanilla -> InteractableVClipSettings.Vanilla(
            paperBypass = vanilla.paperBypass,
            fullPacket = vanilla.fullPacket,
        )
        folia -> InteractableVClipSettings.Folia(
            movementPackets = folia.movementPackets,
            fullPacket = folia.fullPacket,
        )
        else -> error("Unsupported Interactable VClip profile ${choice.activeMode.name}")
    }

    sealed class Choice(
        name: String,
        final override val parent: ModeValueGroup<Choice>,
    ) : Mode(name)

    class Vanilla(parent: ModeValueGroup<Choice>) : Choice("Vanilla", parent) {
        val paperBypass by boolean("PaperBypass", false)
        val fullPacket by boolean("FullPacket", false)
    }

    class Folia(parent: ModeValueGroup<Choice>) : Choice("Folia", parent) {
        val movementPackets by int("MovementPackets", 5, 1..5, "packets")
        val fullPacket by boolean("FullPacket", false)
    }
}

internal data class InteractableBlockFilter(
    val mode: Filter,
    val blocks: Set<Block>,
) {
    operator fun contains(block: Block): Boolean = mode(block, blocks)
}

internal data class InteractableSettingsSnapshot(
    val maxRange: Double,
    val interactionRange: Double,
    val filter: InteractableBlockFilter,
    val containerVehicles: Boolean,
    val routing: InteractableRoutingSettings,
    val surfaceFallback: InteractableSurfaceFallbackSettings,
    val openRetries: Int,
    val openTimeoutTicks: Int,
    val routeTimeoutTicks: Int,
    val holdTimeoutTicks: Int,
)

internal data class InteractableRoutingSettings(
    val maxCost: Int,
    val diagonal: Boolean,
    val lineOfSightShortcuts: Boolean,
    val stepDistance: Double,
    val stepDelayTicks: Int,
    val nodesPerTick: Int,
    val renderPath: Boolean,
)

internal data class InteractableSurfaceFallbackSettings(
    val enabled: Boolean,
    val maxRise: Int,
    val horizontalSearch: Int,
    val doNotClipAroundBedrock: Boolean,
    val transport: InteractableVClipSettings,
)

internal sealed interface InteractableVClipSettings {
    data class Vanilla(
        val paperBypass: Boolean,
        val fullPacket: Boolean,
    ) : InteractableVClipSettings

    data class Folia(
        val movementPackets: Int,
        val fullPacket: Boolean,
    ) : InteractableVClipSettings
}
