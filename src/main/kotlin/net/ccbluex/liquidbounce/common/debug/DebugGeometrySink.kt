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
package net.ccbluex.liquidbounce.common.debug

import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB

sealed interface DebugGeometry

data class DebuggedPoint(
    val position: Vec3,
    val argb: Int,
    val size: Double = 0.2,
) : DebugGeometry

data class DebuggedLineSegment(
    val from: Vec3,
    val to: Vec3,
    val argb: Int,
) : DebugGeometry

data class DebuggedBox(
    val box: AABB,
    val argb: Int,
) : DebugGeometry

data class DebuggedGeometryCollection(val geometries: List<DebugGeometry>) : DebugGeometry

fun interface DebugGeometryAdapter {
    fun publish(owner: Any, name: String, geometry: () -> DebugGeometry)
}

fun interface DebugParameterAdapter {
    fun publish(owner: Any, name: String, value: () -> Any?)
}

object DebugGeometrySink {

    private val DISABLED = DebugGeometryAdapter { _, _, _ -> }

    @Volatile
    private var adapter: DebugGeometryAdapter = DISABLED

    @JvmStatic
    @Synchronized
    fun install(adapter: DebugGeometryAdapter) {
        check(this.adapter === DISABLED) { "Debug geometry adapter is already installed" }
        this.adapter = adapter
    }

    fun publishPoint(owner: Any, name: String, point: () -> DebuggedPoint) {
        adapter.publish(owner, name, point)
    }

    fun publish(owner: Any, name: String, geometry: () -> DebugGeometry) {
        adapter.publish(owner, name, geometry)
    }

    @Synchronized
    internal fun <T> withSinkForTest(candidate: DebugGeometryAdapter?, block: () -> T): T {
        val previous = adapter
        adapter = candidate ?: DISABLED
        return try {
            block()
        } finally {
            adapter = previous
        }
    }
}

object DebugParameterSink {

    private val DISABLED = DebugParameterAdapter { _, _, _ -> }

    @Volatile
    private var adapter: DebugParameterAdapter = DISABLED

    @JvmStatic
    @Synchronized
    fun install(adapter: DebugParameterAdapter) {
        check(this.adapter === DISABLED) { "Debug parameter adapter is already installed" }
        this.adapter = adapter
    }

    fun publish(owner: Any, name: String, value: () -> Any?) {
        adapter.publish(owner, name, value)
    }
}
