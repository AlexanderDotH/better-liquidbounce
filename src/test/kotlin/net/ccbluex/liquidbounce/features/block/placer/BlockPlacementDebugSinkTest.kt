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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.common.debug.DebugGeometryAdapter
import net.ccbluex.liquidbounce.common.debug.DebugGeometrySink
import net.ccbluex.liquidbounce.common.debug.DebuggedPoint
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BlockPlacementDebugSinkTest {

    @Test
    fun `disabled sink preserves lazy point creation`() {
        var evaluated = false

        DebugGeometrySink.withSinkForTest(null) {
            DebugGeometrySink.publishPoint(this, "PlacementTarget") {
                evaluated = true
                DebuggedPoint(Vec3.ZERO, 0)
            }
        }

        assertFalse(evaluated)
    }

    @Test
    fun `installed sink receives exact owner label point color and size`() {
        val owner = Any()
        var capturedOwner: Any? = null
        var capturedName: String? = null
        var capturedPoint: DebuggedPoint? = null
        val adapter = DebugGeometryAdapter { incomingOwner, name, geometry ->
            capturedOwner = incomingOwner
            capturedName = name
            capturedPoint = geometry() as DebuggedPoint
        }

        DebugGeometrySink.withSinkForTest(adapter) {
            DebugGeometrySink.publishPoint(owner, "PlacementTarget") {
                DebuggedPoint(Vec3(1.5, 64.5, -2.5), Color4b.GREEN.with(a = 100).argb)
            }
        }

        assertSame(owner, capturedOwner)
        assertEquals("PlacementTarget", capturedName)
        assertEquals(Vec3(1.5, 64.5, -2.5), capturedPoint?.position)
        assertEquals(Color4b.GREEN.with(a = 100).argb, capturedPoint?.argb)
        assertEquals(0.2, capturedPoint?.size)
    }

    @Test
    fun `planning publishes after reach validation and before sneak and rotation`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block/placer/BlockPlacerPlanning.kt"
        ))
        val reach = source.indexOf("if (!canReach(placementTarget.interactedBlockPos, placementTarget.rotation))")
        val debug = source.indexOf("DebugGeometrySink.publishPoint(this, \"PlacementTarget\")")
        val sneak = source.indexOf("if (placementTarget.interactedBlockPos.state.isInteractable)")
        val rotation = source.indexOf("if (rotationMode.activeMode(entry.booleanValue, pos.immutable(), placementTarget))")

        assertTrue(listOf(reach, debug, sneak, rotation).all { it >= 0 })
        assertTrue(reach < debug && debug < sneak && sneak < rotation)
        assertTrue(source.contains("DebuggedPoint(pos.center, Color4b.GREEN.with(a = 100).argb)"))
        assertFalse(source.contains("ModuleDebug"))
    }
}
