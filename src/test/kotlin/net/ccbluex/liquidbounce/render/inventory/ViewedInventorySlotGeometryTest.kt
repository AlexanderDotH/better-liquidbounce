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
package net.ccbluex.liquidbounce.render.inventory

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ViewedInventorySlotGeometryTest {

    @Test
    fun `slot hitbox keeps one-pixel inclusive leading margin`() {
        assertTrue(isPointOverViewedInventorySlot(12, 34, 11.0, 33.0, 16))
        assertTrue(isPointOverViewedInventorySlot(12, 34, 27.999, 50.999, 16))
    }

    @Test
    fun `slot hitbox keeps exclusive trailing margin`() {
        assertFalse(isPointOverViewedInventorySlot(12, 34, 29.0, 40.0, 16))
        assertFalse(isPointOverViewedInventorySlot(12, 34, 20.0, 51.0, 16))
    }

    @Test
    fun `render extraction keeps matrix cursor and tooltip order`() {
        assertInOrder(
            screenSource,
            "context.pose().pushMatrix()",
            "context.pose().translate(x.toFloat(), y.toFloat())",
            "drawSlotsAndFindHoveredSlot(context, handler, mouseX, mouseY)",
            "val cursorStack = handler.carried",
            "drawItem(context, cursorStack, mouseX - x - 8, mouseY - y - 8)",
            "context.pose().popMatrix()",
            "if (cursorStack.isEmpty && hoveredSlot != null && hoveredSlot.hasItem())",
            "context.setTooltipForNextFrame(",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val screenSource: String = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/render/inventory",
                "ViewedInventoryScreen.kt",
            ),
        )
    }
}
