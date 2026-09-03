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

import net.minecraft.resources.Identifier

data class ChatTabSpec(
    val id: String,
    val label: String,
    val icon: Identifier,
    val contentWidth: Int,
    val selected: Boolean = false,
    val color: Int = -1,
    val status: ChatConnectionStatus = ChatConnectionStatus.CONNECTED,
)

data class ChatTabBounds(
    val id: String,
    val label: String,
    val icon: Identifier,
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

    const val ROW_HEIGHT = 20
    const val ICON_SIZE = 12
    const val ICON_GAP = 5
    private const val EDGE_MARGIN = 2
    private const val TAB_GAP = 3
    private const val HORIZONTAL_PADDING = 10

    @JvmStatic
    fun arrangeSide(
        tabs: List<ChatTabSpec>,
        viewportWidth: Int,
        requestedLeft: Int,
        bottom: Int,
    ): List<ChatTabBounds> {
        if (tabs.isEmpty()) return emptyList()

        val maxLeft = (viewportWidth - EDGE_MARGIN - 1).coerceAtLeast(EDGE_MARGIN)
        val left = requestedLeft.coerceIn(EDGE_MARGIN, maxLeft)
        val availableWidth = (viewportWidth - EDGE_MARGIN - left).coerceAtLeast(1)
        val width = tabs.maxOf {
            it.contentWidth.coerceAtLeast(0) + ICON_SIZE + ICON_GAP + HORIZONTAL_PADDING
        }.coerceAtMost(availableWidth)
        val totalHeight = tabs.size * ROW_HEIGHT + (tabs.size - 1) * TAB_GAP
        var top = (bottom - totalHeight).coerceAtLeast(EDGE_MARGIN)

        return tabs.map { tab ->
            ChatTabBounds(
                id = tab.id,
                label = tab.label,
                icon = tab.icon,
                selected = tab.selected,
                color = tab.color,
                status = tab.status,
                left = left,
                top = top,
                right = left + width,
                bottom = top + ROW_HEIGHT,
            ).also { top = it.bottom + TAB_GAP }
        }
    }

    @JvmStatic
    fun hitTest(bounds: List<ChatTabBounds>, x: Double, y: Double): String? =
        bounds.firstOrNull { it.contains(x, y) }?.id
}
