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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import net.ccbluex.liquidbounce.common.debug.DebugGeometrySink
import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.common.debug.DebugGeometry
import net.ccbluex.liquidbounce.common.debug.DebuggedGeometryCollection
import net.ccbluex.liquidbounce.common.debug.DebuggedLineSegment
import net.ccbluex.liquidbounce.common.debug.DebuggedBox
import net.ccbluex.liquidbounce.common.debug.DebuggedOwner
import net.ccbluex.liquidbounce.common.debug.DebuggedPoint
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal object DebugGeometrySinkAdapter {

    fun install() {
        DebugGeometrySink.install { owner, name, geometry ->
            val debugOwner = owner.debugOwner()
            ModuleDebug.run {
                debugOwner.debugGeometry(name) {
                    geometry().toModuleGeometry()
                }
            }
        }
        DebugParameterSink.install { owner, name, value ->
            val debugOwner = owner.debugOwner()
            ModuleDebug.run {
                debugOwner.debugParameter(name, value)
            }
        }
    }

    private fun Any.debugOwner(): DebuggedOwner = this as? DebuggedOwner ?: object : DebuggedOwner {
        override val debugOwnerId = this@debugOwner.javaClass.name
    }

    private fun DebugGeometry.toModuleGeometry(): ModuleDebug.DebuggedGeometry = when (this) {
        is DebuggedPoint -> ModuleDebug.DebuggedPoint(position, Color4b(argb), size)
        is DebuggedLineSegment -> ModuleDebug.DebuggedLineSegment(from, to, Color4b(argb))
        is DebuggedBox -> ModuleDebug.DebuggedBox(box, Color4b(argb))
        is DebuggedGeometryCollection -> ModuleDebug.DebugCollection(geometries.map { it.toModuleGeometry() })
    }
}
