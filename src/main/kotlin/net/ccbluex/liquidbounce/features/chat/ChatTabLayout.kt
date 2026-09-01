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
package net.ccbluex.liquidbounce.features.chat

data class ChatTabSpec(
    val id: String,
    val label: String,
    val contentWidth: Int,
    val selected: Boolean = false,
    val color: Int = -1,
    val status: ChatConnectionStatus = ChatConnectionStatus.CONNECTED,
)

data class ChatTabBounds(
    val id: String,
    val label: String,
    val selected: Boolean,
    val color: Int,
    val status: ChatConnectionStatus,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    fun contains(x: Double, y: Double) = x >= left && x < right && y >= top && y < bottom
}

object ChatTabLayout {

    const val ROW_HEIGHT = 12
    private const val EDGE_MARGIN = 2
    private const val TAB_GAP = 2
    private const val HORIZONTAL_PADDING = 8

    @JvmStatic
    fun arrange(tabs: List<ChatTabSpec>, viewportWidth: Int, rowTop: Int): List<ChatTabBounds> {
        if (tabs.isEmpty()) return emptyList()

        val innerWidth = (viewportWidth - EDGE_MARGIN * 2).coerceAtLeast(tabs.size)
        val gap = if (tabs.size == 1) 0 else minOf(TAB_GAP, (innerWidth - tabs.size) / (tabs.size - 1))
        val tabWidth = innerWidth - gap * (tabs.size - 1)
        val preferred = tabs.map { it.contentWidth.coerceAtLeast(0) + HORIZONTAL_PADDING }
        val widths = if (preferred.sum() <= tabWidth) preferred else distribute(tabWidth, tabs.size)

        var left = EDGE_MARGIN
        return tabs.mapIndexed { index, tab ->
            ChatTabBounds(
                id = tab.id,
                label = tab.label,
                selected = tab.selected,
                color = tab.color,
                status = tab.status,
                left = left,
                top = rowTop,
                right = left + widths[index],
                bottom = rowTop + ROW_HEIGHT,
            ).also { left = it.right + gap }
        }
    }

    @JvmStatic
    fun hitTest(bounds: List<ChatTabBounds>, x: Double, y: Double): String? =
        bounds.firstOrNull { it.contains(x, y) }?.id

    private fun distribute(width: Int, count: Int) =
        List(count) { width / count + if (it < width % count) 1 else 0 }
}
