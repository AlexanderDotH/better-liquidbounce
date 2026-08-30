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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class MaceKillEndpointSearchRequest(
    val origin: Vec3,
    val targetPosition: Vec3,
    val minimumClearance: Double,
    val maximumRadius: Double,
) {
    init {
        require(origin.hasFiniteEndpointCoordinates() && targetPosition.hasFiniteEndpointCoordinates()) {
            "Endpoint positions must be finite"
        }
        require(minimumClearance.isFinite() && minimumClearance > 0.0) {
            "Endpoint clearance must be finite and positive"
        }
        require(maximumRadius.isFinite() && maximumRadius >= minimumClearance) {
            "Endpoint radius must contain the minimum clearance"
        }
    }
}

/** Searches the complete melee shell rather than assuming the origin-facing side is unobstructed. */
internal object MaceKillEndpointPlanner {

    fun find(
        request: MaceKillEndpointSearchRequest,
        isReady: (Vec3) -> Boolean,
    ): Vec3? = candidates(request).firstOrNull(isReady)

    fun candidates(request: MaceKillEndpointSearchRequest): List<Vec3> {
        val directions = endpointDirections(request)
        val radii = endpointRadii(request)
        return buildList {
            for (verticalOffset in ENDPOINT_VERTICAL_OFFSETS) {
                for (radius in radii) {
                    for (direction in directions) {
                        add(request.targetPosition.add(direction.scale(radius)).add(0.0, verticalOffset, 0.0))
                    }
                }
            }
        }.distinct().sortedBy(request.origin::distanceToSqr)
    }

    private fun endpointDirections(request: MaceKillEndpointSearchRequest): List<Vec3> {
        val towardOrigin = Vec3(
            request.origin.x - request.targetPosition.x,
            0.0,
            request.origin.z - request.targetPosition.z,
        ).let { direction ->
            direction.takeIf { it.lengthSqr() > ENDPOINT_EPSILON_SQUARED }?.normalize() ?: Vec3(1.0, 0.0, 0.0)
        }
        return buildList(ENDPOINT_ANGLE_SAMPLES + 1) {
            add(towardOrigin)
            repeat(ENDPOINT_ANGLE_SAMPLES) { index ->
                val angle = 2.0 * PI * index / ENDPOINT_ANGLE_SAMPLES
                add(Vec3(cos(angle), 0.0, sin(angle)))
            }
        }.distinct()
    }

    private fun endpointRadii(request: MaceKillEndpointSearchRequest): List<Double> = buildList {
        var radius = request.minimumClearance
        while (radius < request.maximumRadius) {
            add(radius)
            radius += ENDPOINT_RADIUS_STEP
        }
        add(request.maximumRadius)
    }.distinct()
}

private val ENDPOINT_VERTICAL_OFFSETS = listOf(0.0, 1.0, -1.0, 2.0, -2.0)
private const val ENDPOINT_ANGLE_SAMPLES = 16
private const val ENDPOINT_RADIUS_STEP = 0.7
private const val ENDPOINT_EPSILON_SQUARED = 1.0E-12

private fun Vec3.hasFiniteEndpointCoordinates(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
