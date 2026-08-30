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

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DeathEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.runtime.MinecraftReachInteractableRuntime
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.MinecraftInteractableInteractionPort
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

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

    internal val runtimeDelegate = lazy {
        MinecraftReachInteractableRuntime(
            packetRateLimitDispositionPort = PacketRateLimitAdapter,
            targetPort = TargetPortAdapter(),
            routePort = RoutePortAdapter(),
            interactionPort = MinecraftInteractableInteractionPort,
        )
    }
    internal val runtime by runtimeDelegate
    internal var reportedStatus: InteractableRuntimeStatus? = null

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
        if (!runtimeDelegate.isInitialized()) return@handler
        runtime.captureContainerPacket(event)
        if (!runtime.active) return@handler
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
        ReachInteractablePathRenderer.render(event, snapshot)
    }

    internal val runtimeStatus: InteractableRuntimeStatus?
        get() = runtimeDelegate.takeIf { it.isInitialized() }?.value?.status

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
