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
package net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.baritone.BaritoneFeature
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute

/** Publishes lightweight Baritone state at most five times per second while its dashboard is visible. */
object BaritoneEventPublisher : EventListener {

    init {
        EventManager.registerEventClass(BaritoneStateEvent::class.java)
        EventManager.registerEventClass(BaritoneRouteEvent::class.java)
        EventManager.registerEventClass(BaritoneLogEvent::class.java)
    }

    private val cursor = BaritonePublicationCursor()
    private var ticks = 0

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (++ticks % PUBLICATION_INTERVAL_TICKS != 0) return@handler
        if (!BaritoneFeature.isDashboardVisible()) return@handler
        val facade = BaritoneFeature.facadeOrNull() ?: return@handler

        val snapshot = facade.snapshot()
        EventManager.callEvent(BaritoneStateEvent(snapshot.revision.value, snapshot.toInteropDto()))

        val route = facade.route()
        if (cursor.acceptRoute(route)) {
            EventManager.callEvent(BaritoneRouteEvent(route.revision.value, route.toInteropDto()))
        }
        cursor.newLogs(snapshot.logs).forEach { entry ->
            EventManager.callEvent(BaritoneLogEvent(entry.revision.value, entry.toInteropDto()))
        }
    }

    private const val PUBLICATION_INTERVAL_TICKS = 4
}

internal class BaritonePublicationCursor {

    private var routeRevision = BaritoneRevision.ZERO
    private var logRevision = BaritoneRevision.ZERO

    fun acceptRoute(route: BaritoneRoute): Boolean {
        if (route.revision <= routeRevision) return false
        routeRevision = route.revision
        return true
    }

    fun newLogs(entries: Collection<BaritoneLogEntry>): List<BaritoneLogEntry> {
        val fresh = entries.asSequence()
            .filter { it.revision > logRevision }
            .distinctBy { it.revision }
            .sortedBy { it.revision }
            .toList()
        fresh.lastOrNull()?.let { logRevision = it.revision }
        return fresh
    }
}
