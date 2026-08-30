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

import baritone.api.pathing.path.IPathExecutor
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoutePoint

internal fun BaritoneAdapterContext.refreshAdapterRoute() {
    val points = flightCoordinator.route().mapTo(ArrayList()) { position ->
        BaritoneRoutePoint(position.x, position.y, position.z)
    }
    if (points.isEmpty()) {
        appendAdapterPath(points, baritone.pathingBehavior.current)
        appendAdapterPath(points, baritone.pathingBehavior.next)
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

internal fun BaritoneAdapterContext.invalidateAdapterRoute(forceRevision: Boolean) {
    if (!forceRevision && currentRoute.points.isEmpty()) return
    currentRoute = BaritoneRoute(revisions.next())
}

private fun appendAdapterPath(points: MutableList<BaritoneRoutePoint>, executor: IPathExecutor?) {
    executor ?: return
    val positions = executor.path.positions()
    val start = executor.position.coerceIn(0, positions.size)
    for (position in positions.drop(start)) {
        val point = BaritoneRoutePoint(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        if (points.lastOrNull() != point) points += point
    }
}
