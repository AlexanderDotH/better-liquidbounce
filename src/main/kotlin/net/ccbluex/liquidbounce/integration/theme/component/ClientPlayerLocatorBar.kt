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

@file:JvmName("ClientLocatorFallbackKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.common.interop.ModernLocatorMarker
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.waypoints.PartialTickSupplier
import net.minecraft.world.waypoints.TrackedWaypoint
import net.minecraft.world.waypoints.Waypoint
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Supplies ephemeral client-known player waypoints when a server sends none.
 *
 * The real [net.minecraft.client.waypoints.ClientWaypointManager] remains untouched so server
 * add/remove packets keep full ownership of its state.
 */
object ClientPlayerLocatorBar {

    @JvmStatic
    fun shouldShowLocator(serverHasWaypoints: Boolean): Boolean {
        if (serverHasWaypoints) {
            return true
        }

        val enabled = isClientLocatorBarEnabled()
        return shouldUseClientLocatorFallback(
            serverHasWaypoints = false,
            fallbackEnabled = enabled,
            hasEligiblePlayers = enabled && eligiblePlayers().isNotEmpty(),
        )
    }

    @JvmStatic
    fun appendFallbackWaypoints(
        serverHasWaypoints: Boolean,
        cameraEntity: Entity,
        renderer: Consumer<TrackedWaypoint>,
    ) {
        val enabled = isClientLocatorBarEnabled()
        val players = if (enabled) eligiblePlayers() else emptyList()
        if (!shouldUseClientLocatorFallback(serverHasWaypoints, enabled, players.isNotEmpty())) {
            return
        }

        players
            .sortedByDescending { cameraEntity.distanceToSqr(it) }
            .forEach { player ->
                val icon = Waypoint.Icon().cloneAndAssignStyle(player)
                renderer.accept(TrackedWaypoint.setPosition(player.uuid, icon, player.blockPosition()))
            }
    }

    @JvmStatic
    fun extractPlayerHead(
        context: GuiGraphicsExtractor,
        tickCounter: DeltaTracker,
        cameraEntity: Entity,
        waypoint: TrackedWaypoint,
    ) {
        if (!isClientLocatorBarEnabled()) {
            return
        }

        val playerId = waypoint.id().left().orElse(null) ?: return
        if (playerId == cameraEntity.uuid) {
            return
        }

        val level = mc.level ?: return
        val connection = mc.connection ?: return
        val loadedPlayer = level.players().firstOrNull { it.uuid == playerId }
        val skin = loadedPlayer?.skin ?: connection.getPlayerInfo(playerId)?.skin ?: return
        val partialTickSupplier = PartialTickSupplier { entity ->
            tickCounter.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity))
        }
        val yaw = waypoint.yawAngleToCamera(level, mc.gameRenderer.mainCamera(), partialTickSupplier)
        val markerX = resolveLocatorMarkerX(context.guiWidth(), yaw) ?: return
        val markerY = context.guiHeight() - LOCATOR_BOTTOM_OFFSET - MARKER_TOP_OVERHANG

        PlayerFaceExtractor.extractRenderState(
            context,
            skin,
            markerX + HEAD_INSET,
            markerY + HEAD_INSET,
            HEAD_SIZE,
        )
    }

    fun snapshotMarkers(): List<ModernLocatorMarker> {
        val player = mc.player ?: return emptyList()
        val cameraEntity = mc.cameraEntity ?: player
        val level = mc.level ?: return emptyList()
        val connection = mc.connection ?: return emptyList()
        val waypointManager = connection.waypointManager
        val waypoints = mutableListOf<TrackedWaypoint>()

        waypointManager.forEachWaypoint(cameraEntity, waypoints::add)
        appendFallbackWaypoints(
            serverHasWaypoints = waypointManager.hasWaypoints(),
            cameraEntity = cameraEntity,
            renderer = waypoints::add,
        )

        val deltaTracker = mc.deltaTracker
        val tickRateManager = level.tickRateManager()
        val partialTickSupplier = PartialTickSupplier { entity ->
            deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity))
        }

        return waypoints.mapNotNull { waypoint ->
            val uuid = waypoint.id().left().orElse(null)
            if (uuid == cameraEntity.uuid) {
                return@mapNotNull null
            }

            val yaw = waypoint.yawAngleToCamera(level, mc.gameRenderer.mainCamera(), partialTickSupplier)
            val offset = resolveLocatorMarkerOffset(yaw) ?: return@mapNotNull null
            val playerInfo = uuid?.let(connection::getPlayerInfo)
            val rawId = uuid?.toString() ?: waypoint.id().right().orElse("waypoint")
            ModernLocatorMarker(
                id = rawId,
                label = playerInfo?.profile?.name ?: rawId,
                offset = offset,
                elevation = markerElevation(waypoint, level, partialTickSupplier),
                distance = markerDistance(waypoint, cameraEntity),
                color = markerColor(waypoint, rawId),
                kind = if (playerInfo != null) "player" else "waypoint",
                playerUuid = playerInfo?.profile?.id?.toString(),
                style = waypoint.icon().style.identifier().toString(),
            )
        }
    }

    private fun markerDistance(waypoint: TrackedWaypoint, cameraEntity: Entity): Int {
        val squared = waypoint.distanceSquared(cameraEntity)
        return if (squared.isFinite()) sqrt(squared.coerceAtLeast(0.0)).roundToInt() else Int.MAX_VALUE
    }

    private fun markerElevation(
        waypoint: TrackedWaypoint,
        level: ClientLevel,
        partialTickSupplier: PartialTickSupplier,
    ) = when (waypoint.pitchDirectionToCamera(level, mc.gameRenderer, partialTickSupplier)) {
        TrackedWaypoint.PitchDirection.UP -> "above"
        TrackedWaypoint.PitchDirection.DOWN -> "below"
        TrackedWaypoint.PitchDirection.NONE -> "level"
    }

    private fun markerColor(waypoint: TrackedWaypoint, rawId: String): Int = waypoint.icon().color.orElseGet {
        ARGB.setBrightness(ARGB.color(255, rawId.hashCode()), 0.9f)
    } and 0xFFFFFF

    private fun eligiblePlayers(): List<AbstractClientPlayer> {
        val localPlayer = mc.player ?: return emptyList()
        val level = mc.level ?: return emptyList()
        val connection = mc.connection ?: return emptyList()

        return level.players()
            .asSequence()
            .filter { player ->
                isEligibleLocatorPlayer(
                    isLocal = player === localPlayer,
                    isSpectator = player.isSpectator,
                    isRemoved = player.isRemoved,
                    isAlive = player.isAlive,
                    isBot = ModuleAntiBot.isBot(player),
                    hasPlayerInfo = connection.getPlayerInfo(player.uuid) != null,
                    isCrouching = player.isCrouching,
                    isInvisible = player.isInvisible,
                )
            }
            .distinctBy { it.uuid }
            .toList()
    }

    private fun isClientLocatorBarEnabled(): Boolean = resolveClientLocatorFallbackPolicy(
        hudRunning = ModuleHud.running,
        appearanceHidden = HideAppearance.isHidingNow,
        hudTheme = ModuleHud.theme,
        bundledHud = isBundledHudRendered(),
    )
}
