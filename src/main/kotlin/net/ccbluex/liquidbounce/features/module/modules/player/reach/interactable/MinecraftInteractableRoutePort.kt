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
@file:JvmName("MinecraftInteractableRouteAdapterKt")

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteFailure
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlanner
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteProgress
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteRequest
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSettings
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteTargetKind
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteTask
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableResolvedTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetLock
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

internal class MinecraftInteractableRoutePort : ControllerRoutePort<
    InteractableRuntimeTarget,
    InteractableSessionRoute<InteractablePacketInstruction>,
    InteractableRenderSnapshot,
> {
    override fun begin(
        target: InteractableRuntimeTarget,
        origin: Vec3,
        settings: InteractableSettingsSnapshot,
    ): ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
        val resolvedTarget = target as InteractableResolvedTarget
        val level = requireNotNull(mc.level) { "Interactable route requires a world" }
        val player = requireNotNull(mc.player) { "Interactable route requires a player" }
        val routeWorld = MinecraftInteractableRouteWorld(level, player)
        val goalWorld = MinecraftInteractableGoalWorld(level, player, resolvedTarget, settings.interactionRange)
        val targetNode = goalWorld.targetNode ?: return FailedControllerRouteTask("TARGET_MISSING")
        val goals = resolveInteractableGoalStances(
            targetNode = targetNode,
            origin = origin,
            interactionRange = settings.interactionRange,
            canStand = { routeWorld.isLoaded(it) && routeWorld.isPassable(it) && routeWorld.isSupported(it) },
            canInteract = goalWorld::canInteract,
        )
        if (goals.isEmpty()) return FailedControllerRouteTask(InteractableRouteFailure.NO_VALID_GOAL.name)

        val request = InteractableRouteRequest(
            origin = origin,
            goalStances = goals,
            targetKind = when (resolvedTarget.lock) {
                is InteractableTargetLock.Block -> InteractableRouteTargetKind.STATIONARY_BLOCK
                is InteractableTargetLock.ContainerVehicle -> InteractableRouteTargetKind.MOVING_CONTAINER
            },
            settings = settings.toRouteSettings(),
        )
        val task = InteractableRoutePlanner(routeWorld).begin(request)
        val fallSafety = VClipFallSafetyContext(
            initialFallDistance = player.fallDistance.toDouble(),
            safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        )
        return MinecraftControllerRouteTask(task, settings, fallSafety)
    }
}

private class MinecraftControllerRouteTask(
    private val delegate: InteractableRouteTask,
    private val settings: InteractableSettingsSnapshot,
    private val fallSafety: VClipFallSafetyContext,
) : ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
    override fun advance(
        nodes: Int,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        when (val progress = delegate.advance(nodes)) {
            is InteractableRouteProgress.Running -> ControllerRouteProgress.Running(
                InteractableRenderSnapshot.Planning(progress.snapshot),
            )
            is InteractableRouteProgress.Failed -> ControllerRouteProgress.Failed(progress.reason.name)
            is InteractableRouteProgress.Ready -> compile(progress.plan)
        }

    override fun cancel() = delegate.cancel()

    private fun compile(
        plan: InteractableRoutePlan,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        when (
            val result = InteractableRouteCompiler.compile(
                plan,
                settings.routing.stepDistance,
                settings.surfaceFallback.maxClipDistance.toDouble(),
                settings.surfaceFallback.transport,
                fallSafety,
            )
        ) {
            is InteractableRouteCompileResult.Ready -> ControllerRouteProgress.Ready(
                result.route,
                InteractableRenderSnapshot.Route(plan.renderSnapshot),
            )
            InteractableRouteCompileResult.VClipUnavailable -> ControllerRouteProgress.Failed("VCLIP_UNAVAILABLE")
            InteractableRouteCompileResult.VClipDistanceExceeded ->
                ControllerRouteProgress.Failed(InteractableRouteFailure.CLIP_DISTANCE_EXCEEDED.name)
        }
}

private class FailedControllerRouteTask(
    private val reason: String,
) : ControllerRouteTask<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> {
    override fun advance(
        nodes: Int,
    ): ControllerRouteProgress<InteractableSessionRoute<InteractablePacketInstruction>, InteractableRenderSnapshot> =
        ControllerRouteProgress.Failed(reason)

    override fun cancel() = Unit
}

private fun InteractableSettingsSnapshot.toRouteSettings(): InteractableRouteSettings {
    val maxIterations = (routing.nodesPerTick.toLong() * routeTimeoutTicks.toLong())
        .coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    val directMaxIterations = (routing.nodesPerTick.toLong() * DIRECT_PROBE_TICKS)
        .coerceIn(1L, maxIterations.toLong()).toInt()
    return InteractableRouteSettings(
        allowDiagonal = routing.diagonal,
        maxCost = routing.maxCost.toDouble(),
        maxIterations = maxIterations,
        directMaxIterations = directMaxIterations,
        lineOfSightShortcuts = routing.lineOfSightShortcuts,
        surfaceFallback = surfaceFallback.enabled,
        maxRise = surfaceFallback.maxRise,
        horizontalSearch = surfaceFallback.horizontalSearch,
        protectBedrock = surfaceFallback.doNotClipAroundBedrock,
        maxClipDistance = surfaceFallback.maxClipDistance,
    )
}

private const val DIRECT_PROBE_TICKS = 20L
