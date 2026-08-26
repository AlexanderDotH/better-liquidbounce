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
 */
package net.ccbluex.liquidbounce.features.module.modules.world.trialchamber

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** Render-only resource kinds, deliberately independent from the runtime snapshot representation. */
internal enum class TrialChamberRenderTargetKind {
    SPAWNER,
    NORMAL_VAULT,
    OMINOUS_VAULT,
    CHEST,
    BARREL,
    POT,
    DISPENSER,
}

/** Independently configurable visibility for every Trial Chamber resource kind. */
internal data class TrialChamberRenderFilters(
    val spawners: Boolean = true,
    val normalVaults: Boolean = true,
    val ominousVaults: Boolean = true,
    val chests: Boolean = true,
    val barrels: Boolean = true,
    val pots: Boolean = true,
    val dispensers: Boolean = true,
) {
    fun includes(kind: TrialChamberRenderTargetKind): Boolean = when (kind) {
        TrialChamberRenderTargetKind.SPAWNER -> spawners
        TrialChamberRenderTargetKind.NORMAL_VAULT -> normalVaults
        TrialChamberRenderTargetKind.OMINOUS_VAULT -> ominousVaults
        TrialChamberRenderTargetKind.CHEST -> chests
        TrialChamberRenderTargetKind.BARREL -> barrels
        TrialChamberRenderTargetKind.POT -> pots
        TrialChamberRenderTargetKind.DISPENSER -> dispensers
    }
}

/** Values copied from the module before planning one frame. */
internal data class TrialChamberRenderSettings(
    val maximumDistance: Double = TrialChamberRenderPlanner.DEFAULT_MAXIMUM_DISTANCE,
    val showGlow: Boolean = true,
    val showLabels: Boolean = true,
    val maximumLabels: Int = TrialChamberRenderPlanner.MAXIMUM_LABELS,
    val showVisited: Boolean = false,
    val showCompleted: Boolean = false,
    val filters: TrialChamberRenderFilters = TrialChamberRenderFilters(),
)

/**
 * Narrow adapter model for one immutable snapshot target.
 *
 * [position] is the distance anchor. [worldBox] may cover more than one block, which lets the
 * runtime represent double chests without coupling this planner to block-state details.
 */
internal data class TrialChamberRenderTarget(
    val id: String,
    val kind: TrialChamberRenderTargetKind,
    val position: Vec3,
    val worldBox: AABB,
    val label: String,
    val color: Color4b,
    val visited: Boolean = false,
    val completed: Boolean = false,
)

/** Immutable frame input. The collection is detached from its caller at construction time. */
internal class TrialChamberRenderRequest(
    val cameraPosition: Vec3,
    targets: Collection<TrialChamberRenderTarget>,
    val settings: TrialChamberRenderSettings = TrialChamberRenderSettings(),
) {
    val targets: List<TrialChamberRenderTarget> = java.util.List.copyOf(targets)
}

internal data class TrialChamberGlowBox(
    val targetId: String,
    val kind: TrialChamberRenderTargetKind,
    val distance: Double,
    val worldBox: AABB,
    val color: Color4b,
) {
    val glowMaskColor: Color4b
        get() = color.with(a = FULL_ALPHA)

    private companion object {
        const val FULL_ALPHA = 255
    }
}

internal data class TrialChamberRenderLabel(
    val targetId: String,
    val kind: TrialChamberRenderTargetKind,
    val position: Vec3,
    val text: String,
    val distance: Double,
    val color: Color4b,
)

/** Glow geometry and labels stay separate so the renderer can submit its dedicated Glow source. */
internal data class TrialChamberRenderPlan(
    val glowBoxes: List<TrialChamberGlowBox>,
    val labels: List<TrialChamberRenderLabel>,
) {
    companion object {
        val EMPTY = TrialChamberRenderPlan(emptyList(), emptyList())
    }
}

/** Pure filtering, ordering, and geometry planning for the Trial Chamber tracker. */
internal object TrialChamberRenderPlanner {

    const val DEFAULT_MAXIMUM_DISTANCE = 192.0
    const val MAXIMUM_LABELS = 24

    fun plan(request: TrialChamberRenderRequest): TrialChamberRenderPlan {
        if (request.targets.isEmpty() || request.settings.maximumDistance < 0.0 ||
            !request.settings.showGlow && !request.settings.showLabels
        ) {
            return TrialChamberRenderPlan.EMPTY
        }

        val maximumDistanceSquared = request.settings.maximumDistance * request.settings.maximumDistance
        val plannedTargets = request.targets.asSequence()
            .filter { target -> target.isVisible(request.settings) }
            .map { target -> PlannedTarget(target, target.position.distanceToSqr(request.cameraPosition)) }
            .filter { target -> target.distanceSquared <= maximumDistanceSquared }
            .sortedWith(PLANNED_TARGET_ORDER)
            .toList()

        if (plannedTargets.isEmpty()) return TrialChamberRenderPlan.EMPTY

        val glowBoxes = if (request.settings.showGlow) {
            plannedTargets.map { it.glowBox() }
        } else {
            emptyList()
        }
        val labels = createLabels(plannedTargets, request.settings)
        return TrialChamberRenderPlan(
            java.util.List.copyOf(glowBoxes),
            java.util.List.copyOf(labels),
        )
    }

    private fun TrialChamberRenderTarget.isVisible(settings: TrialChamberRenderSettings): Boolean {
        if (!settings.filters.includes(kind)) return false
        if (visited && !settings.showVisited) return false
        if (completed && !settings.showCompleted) return false
        return true
    }

    private fun createLabels(
        plannedTargets: List<PlannedTarget>,
        settings: TrialChamberRenderSettings,
    ): List<TrialChamberRenderLabel> {
        if (!settings.showLabels || settings.maximumLabels <= 0) return emptyList()

        val labelLimit = settings.maximumLabels.coerceAtMost(MAXIMUM_LABELS)
        return plannedTargets.take(labelLimit).map { it.label() }
    }

    private fun PlannedTarget.glowBox(): TrialChamberGlowBox {
        val distance = sqrt(distanceSquared)
        return TrialChamberGlowBox(
            targetId = target.id,
            kind = target.kind,
            distance = distance,
            worldBox = target.worldBox,
            color = target.color,
        )
    }

    private fun PlannedTarget.label(): TrialChamberRenderLabel {
        val distance = sqrt(distanceSquared)
        return TrialChamberRenderLabel(
            targetId = target.id,
            kind = target.kind,
            position = target.worldBox.labelPosition(),
            text = target.label,
            distance = distance,
            color = target.color,
        )
    }

    private fun AABB.labelPosition(): Vec3 = Vec3(
        (minX + maxX) * 0.5,
        maxY + LABEL_VERTICAL_OFFSET,
        (minZ + maxZ) * 0.5,
    )

    private data class PlannedTarget(
        val target: TrialChamberRenderTarget,
        val distanceSquared: Double,
    )

    private val PLANNED_TARGET_ORDER = compareBy<PlannedTarget> { it.distanceSquared }
        .thenBy { it.target.id }
        .thenBy { it.target.kind.ordinal }
        .thenBy { it.target.position.x }
        .thenBy { it.target.position.y }
        .thenBy { it.target.position.z }

    private const val LABEL_VERTICAL_OFFSET = 0.25
}
