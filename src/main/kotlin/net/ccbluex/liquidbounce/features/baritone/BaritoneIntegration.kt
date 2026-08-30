/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.events.DeathEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.baritone.adapter.BaritoneAdapterMessage
import net.ccbluex.liquidbounce.features.baritone.adapter.BaritoneApiAdapter
import net.ccbluex.liquidbounce.features.baritone.adapter.BaritoneMessageSink
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeConfig
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBaritone
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY

/** Composition root and LiquidBounce lifecycle boundary for the third-party Baritone adapter. */
object BaritoneIntegration : EventListener {

    val facade: BaritoneFacade = BaritoneApiAdapter.create(
        conflictDetector = LiquidBounceBaritoneConflictDetector,
        resumeDelayTicks = { ModuleBaritone.resumeDelayTicks },
        navigationMode = { ModuleBaritone.navigationMode },
        flightRuntimeConfig = {
            ModuleBaritone.flyNavigationConfig.let { config ->
                BaritoneFlightRuntimeConfig(
                    armTimeoutTicks = config.armTimeoutTicks,
                    maxRestarts = config.maxRestarts,
                    retryDistanceBlocks = config.retryDistanceBlocks,
                )
            }
        },
        onAutomationStart = ::activate,
        messageSink = BaritoneMessageSink(::forwardMessage),
    ).also(BaritoneFeature::install)

    fun initialize(): BaritoneFacade = facade

    private fun activate() {
        if (!ModuleBaritone.enabled) ModuleBaritone.enabled = true
    }

    fun shutdown() {
        facade.lifecycle(BaritoneLifecycleEvent.SHUTDOWN)
        BaritoneFeature.uninstall(facade)
    }

    @Suppress("unused")
    private val deathHandler = handler<DeathEvent> {
        facade.lifecycle(BaritoneLifecycleEvent.DEATH)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        facade.lifecycle(BaritoneLifecycleEvent.DISCONNECT)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        val lifecycle = if (event.world == null) {
            BaritoneLifecycleEvent.DISCONNECT
        } else {
            BaritoneLifecycleEvent.DIMENSION_CHANGE
        }
        facade.lifecycle(lifecycle)
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent>(priority = FIRST_PRIORITY) {
        shutdown()
    }

    private fun forwardMessage(message: BaritoneAdapterMessage) {
        val forwarded = message.toLiquidBounceNotification() ?: return
        notification(
            forwarded.title,
            forwarded.message,
            if (forwarded.error) NotificationEvent.Severity.ERROR else NotificationEvent.Severity.INFO,
        )
    }
}

internal data class BaritoneForwardedNotification(
    val title: String,
    val message: String,
    val error: Boolean,
)

internal fun BaritoneAdapterMessage.toLiquidBounceNotification(): BaritoneForwardedNotification? = when (this) {
    is BaritoneAdapterMessage.Log -> null
    is BaritoneAdapterMessage.Notification -> BaritoneForwardedNotification("Baritone", message, error)
    is BaritoneAdapterMessage.Toast -> BaritoneForwardedNotification(title.ifBlank { "Baritone" }, message, false)
}
