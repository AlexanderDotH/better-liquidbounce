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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/** Immutable route settings captured when an Interactable session claims its target. */
internal data class InteractableRouteSettings(
    val allowDiagonal: Boolean,
    val maxCost: Double,
    val maxIterations: Int,
    val lineOfSightShortcuts: Boolean,
    val surfaceFallback: Boolean,
    val maxRise: Int,
    val horizontalSearch: Int,
    val protectBedrock: Boolean,
) {
    init {
        require(maxCost.isFinite() && maxCost > 0.0) { "maxCost must be finite and positive" }
        require(maxIterations > 0) { "maxIterations must be positive" }
        require(maxRise > 0) { "maxRise must be positive" }
        require(horizontalSearch > 0) { "horizontalSearch must be positive" }
    }
}

internal enum class InteractableRouteTargetKind {
    STATIONARY_BLOCK,
    MOVING_CONTAINER,
}

/** One collision-safe standing position from which the locked target can be interacted with. */
internal data class InteractableRouteStance(
    val node: BlockPos,
    val position: Vec3,
) {
    init {
        require(position.hasFiniteCoordinates()) { "A route stance must be finite" }
        require(BlockPos.containing(position) == node) { "A route stance must be inside its node" }
    }
}

internal class InteractableRouteRequest(
    val origin: Vec3,
    goalStances: List<InteractableRouteStance>,
    val targetKind: InteractableRouteTargetKind,
    val settings: InteractableRouteSettings,
) {
    val goalStances: List<InteractableRouteStance> = goalStances.toList()

    init {
        require(origin.hasFiniteCoordinates()) { "Route origin must be finite" }
        require(this.goalStances.isNotEmpty()) { "At least one goal stance is required" }
        require(this.goalStances.map(InteractableRouteStance::node).distinct().size == this.goalStances.size) {
            "Goal stance nodes must be unique"
        }
    }
}

internal enum class InteractableRoutePlanningPhase {
    DIRECT,
    CAVE_EGRESS,
    SURFACE_ANCHOR_SCAN,
    SURFACE_TRAVERSE,
}

internal enum class InteractableRouteFailure {
    CANCELLED,
    NO_VALID_GOAL,
    NO_DIRECT_ROUTE,
    MOVING_TARGET_REQUIRES_DIRECT_ROUTE,
    UNLOADED_WORLD,
    MAX_COST_EXCEEDED,
    MAX_ITERATIONS_EXCEEDED,
    NO_SURFACE,
    MAX_RISE_EXCEEDED,
    HORIZONTAL_SEARCH_EXCEEDED,
    BUILD_HEIGHT_LIMIT,
    BEDROCK_BLOCKED,
    NO_SURFACE_ROUTE,
}

internal data class InteractableRouteSearchSnapshot(
    val phase: InteractableRoutePlanningPhase,
    val expandedThisAdvance: Int,
    val totalExpanded: Int,
    val frontierSize: Int,
)

internal sealed interface InteractableRouteProgress {
    data class Running(val snapshot: InteractableRouteSearchSnapshot) : InteractableRouteProgress
    data class Ready(val plan: InteractableRoutePlan) : InteractableRouteProgress
    data class Failed(val reason: InteractableRouteFailure) : InteractableRouteProgress
}

internal interface InteractableRouteTask {
    fun advance(expansionBudget: Int): InteractableRouteProgress
    fun cancel()
}

internal enum class InteractableRouteKind {
    DIRECT,
    SURFACE,
}

internal enum class InteractableRoutePathKind {
    DIRECT,
    CAVE_EGRESS,
    SURFACE_TRAVERSE,
}

/** A transport-neutral route leg. Packet and VClip emitters adapt these legs at the runtime edge. */
internal sealed interface InteractableRouteSegment {
    val from: Vec3
    val to: Vec3

    fun reversed(): InteractableRouteSegment

    class Path(
        val kind: InteractableRoutePathKind,
        points: List<Vec3>,
    ) : InteractableRouteSegment {

        val points: List<Vec3> = points.toList()

        init {
            require(this.points.isNotEmpty()) { "A route path needs at least one point" }
            require(this.points.all(Vec3::hasFiniteCoordinates)) { "Route path points must be finite" }
        }

        override val from: Vec3
            get() = points.first()

        override val to: Vec3
            get() = points.last()

        override fun reversed() = Path(kind, points.asReversed())

        override fun equals(other: Any?): Boolean = other is Path && kind == other.kind && points == other.points

        override fun hashCode(): Int = 31 * kind.hashCode() + points.hashCode()

        override fun toString(): String = "Path(kind=$kind, points=$points)"
    }

    data class VerticalClip(
        override val from: Vec3,
        override val to: Vec3,
    ) : InteractableRouteSegment {
        init {
            require(from.hasFiniteCoordinates() && to.hasFiniteCoordinates()) {
                "Vertical clip endpoints must be finite"
            }
            require(from.x == to.x && from.z == to.z && from.y != to.y) {
                "A vertical clip must change only Y"
            }
        }

        override fun reversed() = VerticalClip(to, from)
    }
}

internal data class InteractableRouteRenderSnapshot(
    val paths: List<InteractableRouteSegment.Path>,
    val verticalClips: List<InteractableRouteSegment.VerticalClip>,
)

/**
 * A complete outbound route and its mechanically derived exact return.
 *
 * The return never needs a second world search. Each outbound leg exposes its inverse instruction,
 * so confirmed-prefix recovery can retain only the inverse legs for movements the server accepted.
 */
internal class InteractableRoutePlan(
    val kind: InteractableRouteKind,
    outboundSegments: List<InteractableRouteSegment>,
) {

    val outboundSegments: List<InteractableRouteSegment> = outboundSegments.toList()
    val returnSegments: List<InteractableRouteSegment> = this.outboundSegments
        .asReversed()
        .map(InteractableRouteSegment::reversed)

    init {
        require(this.outboundSegments.isNotEmpty()) { "A route plan needs outbound segments" }
        require(this.outboundSegments.zipWithNext().all { (first, second) -> first.to.samePoint(second.from) }) {
            "Outbound route segments must be continuous"
        }
        require(returnSegments.zipWithNext().all { (first, second) -> first.to.samePoint(second.from) }) {
            "Return route segments must be continuous"
        }
        requireShapeMatchesKind()
    }

    val origin: Vec3
        get() = outboundSegments.first().from

    val outboundEndpoint: Vec3
        get() = outboundSegments.last().to

    val returnEndpoint: Vec3
        get() = returnSegments.last().to

    val renderSnapshot = InteractableRouteRenderSnapshot(
        paths = outboundSegments.filterIsInstance<InteractableRouteSegment.Path>(),
        verticalClips = outboundSegments.filterIsInstance<InteractableRouteSegment.VerticalClip>(),
    )

    private fun requireShapeMatchesKind() {
        when (kind) {
            InteractableRouteKind.DIRECT -> require(
                outboundSegments.size == 1 &&
                    (outboundSegments.single() as? InteractableRouteSegment.Path)?.kind ==
                    InteractableRoutePathKind.DIRECT,
            ) { "A direct plan must contain one direct path" }

            InteractableRouteKind.SURFACE -> require(
                outboundSegments.size == 3 &&
                    (outboundSegments[0] as? InteractableRouteSegment.Path)?.kind ==
                    InteractableRoutePathKind.CAVE_EGRESS &&
                    (outboundSegments[1] as? InteractableRouteSegment.Path)?.kind ==
                    InteractableRoutePathKind.SURFACE_TRAVERSE &&
                    outboundSegments[2] is InteractableRouteSegment.VerticalClip,
            ) { "A surface plan must contain egress, surface traversal, and vertical clip legs" }
        }
    }
}

private fun Vec3.hasFiniteCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun Vec3.samePoint(other: Vec3): Boolean = distanceToSqr(other) <= ROUTE_POINT_EPSILON_SQUARED

private const val ROUTE_POINT_EPSILON_SQUARED = 1.0E-12
