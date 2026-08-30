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
package net.ccbluex.liquidbounce.features.command.preset

import net.ccbluex.liquidbounce.utils.text.bold
import net.ccbluex.liquidbounce.utils.text.onClickRun
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.withColor
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.asText
import net.ccbluex.liquidbounce.utils.text.joinToText
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.util.function.IntConsumer

private val TEXT_SPACE: Component = " ".asPlainText()

private sealed interface PaginationEntry {
    data class Page(val value: Int) : PaginationEntry
    data class Ellipsis(val left: Int, val right: Int) : PaginationEntry
}

internal fun paginationPages(
    currentPage: Int,
    maxPage: Int,
    boundaryLimit: Int = 3,
    ellipsisThreshold: Int = 5,
): List<Int> = paginationEntries(currentPage, maxPage, boundaryLimit, ellipsisThreshold)
    .filterIsInstance<PaginationEntry.Page>()
    .map(PaginationEntry.Page::value)

internal fun buildPaginationText(
    currentPage: Int,
    maxPage: Int,
    boundaryLimit: Int = 3,
    ellipsisThreshold: Int = 5,
    sendPage: IntConsumer,
): Component {
    val texts = buildList {
        add(navigationArrow("\u2B9C", currentPage - 1, currentPage == 1, sendPage))
        paginationEntries(currentPage, maxPage, boundaryLimit, ellipsisThreshold).forEach { entry ->
            add(entry.toText(currentPage, sendPage))
        }
        add(navigationArrow("\u2B9E", currentPage + 1, currentPage == maxPage, sendPage))
    }
    return texts.joinToText(TEXT_SPACE)
}

private fun paginationEntries(
    currentPage: Int,
    maxPage: Int,
    boundaryLimit: Int,
    ellipsisThreshold: Int,
): List<PaginationEntry> = when {
    maxPage <= ellipsisThreshold -> (1..maxPage).map(PaginationEntry::Page)
    currentPage <= boundaryLimit -> leadingEntries(maxPage, boundaryLimit)
    currentPage >= maxPage - boundaryLimit + 1 -> trailingEntries(maxPage, boundaryLimit)
    else -> middleEntries(currentPage, maxPage)
}

private fun leadingEntries(maxPage: Int, boundaryLimit: Int) = buildList {
    (1..boundaryLimit).mapTo(this, PaginationEntry::Page)
    add(PaginationEntry.Ellipsis(boundaryLimit + 1, maxPage))
    add(PaginationEntry.Page(maxPage))
}

private fun trailingEntries(maxPage: Int, boundaryLimit: Int) = buildList {
    val firstTrailingPage = maxPage - boundaryLimit + 1
    add(PaginationEntry.Page(1))
    add(PaginationEntry.Ellipsis(2, firstTrailingPage))
    (firstTrailingPage..maxPage).mapTo(this, PaginationEntry::Page)
}

private fun middleEntries(currentPage: Int, maxPage: Int) = buildList {
    add(PaginationEntry.Page(1))
    add(PaginationEntry.Ellipsis(2, currentPage - 1))
    (currentPage - 1..currentPage + 1).mapTo(this, PaginationEntry::Page)
    add(PaginationEntry.Ellipsis(currentPage + 2, maxPage))
    add(PaginationEntry.Page(maxPage))
}

private fun PaginationEntry.toText(currentPage: Int, sendPage: IntConsumer): MutableComponent = when (this) {
    is PaginationEntry.Page -> value.toString().asText().apply {
        if (value == currentPage) disabled().bold(true) else pageAction(value, sendPage)
    }
    is PaginationEntry.Ellipsis -> "…".asText().pageAction((left + right) / 2, sendPage)
}

private fun navigationArrow(
    text: String,
    targetPage: Int,
    isDisabled: Boolean,
    sendPage: IntConsumer,
) = text.asText().apply {
    if (isDisabled) disabled() else pageAction(targetPage, sendPage).withColor(ChatFormatting.GRAY)
}

private fun MutableComponent.disabled() = withColor(ChatFormatting.DARK_GRAY)

private fun MutableComponent.pageAction(page: Int, sendPage: IntConsumer) =
    onHover(HoverEvent.ShowText(page.toString().asPlainText()))
        .onClickRun { sendPage.accept(page) }
