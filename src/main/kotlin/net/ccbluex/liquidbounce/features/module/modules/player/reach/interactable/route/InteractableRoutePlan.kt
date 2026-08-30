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

import net.minecraft.world.phys.Vec3

/** A complete outbound route and its mechanically derived exact return. */
internal class InteractableRoutePlan(
    val kind: InteractableRouteKind,
    outboundSegments: List<InteractableRouteSegment>,
) {
    val outboundSegments = outboundSegments.toList()
    val returnSegments = this.outboundSegments.asReversed().map(InteractableRouteSegment::reversed)

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
        outboundSegments.filterIsInstance<InteractableRouteSegment.Path>(),
        outboundSegments.filterIsInstance<InteractableRouteSegment.VerticalClip>(),
    )

    private fun requireShapeMatchesKind() {
        when (kind) {
            InteractableRouteKind.DIRECT -> require(isDirectShape()) {
                "A direct plan must contain one direct path"
            }
            InteractableRouteKind.CAVE_CLIP -> require(isCaveClipShape()) {
                "A cave clip plan must contain a vertical clip and only cave traversal paths"
            }
            InteractableRouteKind.SURFACE -> require(isSurfaceShape()) {
                "A surface plan must contain egress, surface traversal, and vertical clip legs"
            }
        }
    }

    private fun isDirectShape() = outboundSegments.size == 1 &&
        (outboundSegments.single() as? InteractableRouteSegment.Path)?.kind == InteractableRoutePathKind.DIRECT

    private fun isCaveClipShape() =
        outboundSegments.any { it is InteractableRouteSegment.VerticalClip } &&
            outboundSegments.filterIsInstance<InteractableRouteSegment.Path>()
                .all { it.kind == InteractableRoutePathKind.CAVE_TRAVERSE }

    private fun isSurfaceShape() = outboundSegments.size == 3 &&
        (outboundSegments[0] as? InteractableRouteSegment.Path)?.kind == InteractableRoutePathKind.CAVE_EGRESS &&
        (outboundSegments[1] as? InteractableRouteSegment.Path)?.kind == InteractableRoutePathKind.SURFACE_TRAVERSE &&
        outboundSegments[2] is InteractableRouteSegment.VerticalClip
}

private fun Vec3.samePoint(other: Vec3): Boolean = distanceToSqr(other) <= ROUTE_POINT_EPSILON_SQUARED

private const val ROUTE_POINT_EPSILON_SQUARED = 1.0E-12
