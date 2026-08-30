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
package net.ccbluex.liquidbounce.features.command.preset

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.minecraft.network.chat.Component
import kotlin.math.ceil

/**
 * Builds a general paged query command with one optional integer parameter.
 *
 * @param pageSize the size of a single page. should be greater than 0.
 * @param header the generator function for page header before all items.
 * @param items provides all items. This function should be light-weighted.
 * @param eachRow controls how to render the item in chat HUD.
 *
 * @author MukjepScarlet
 */
fun <T> CommandBuilder.pagedQuery(
    pageSize: Int = 8,
    header: Command.() -> Component,
    items: () -> Collection<T>,
    eachRow: Command.(index: Int, T) -> Component,
): Command {
    require(pageSize > 0) { "pageSize must be greater than 0" }

    fun maxPage() = ceil(items().size.toFloat() / pageSize).toInt()

    fun Command.sendPage(currentPage: Int) {
        val msgId = "C${this.name}#PagedQuery"
        val msgMetadata = MessageMetadata(id = msgId, remove = false)
        fun send(text: Component) = chat(text, metadata = msgMetadata)

        val all = items()
        val maxPage = maxPage()
        val currentPageItems = pageItems(all, currentPage, pageSize)

        mc.gui.hud.chat.removeMessage(msgId) // remove old

        // Header
        send(header(this))
        // Content
        currentPageItems.forEachIndexed { index, item ->
            send(eachRow(this, index, item))
        }
        // Pagination
        if (maxPage > 1) {
            send(buildPaginationText(currentPage, maxPage, sendPage = ::sendPage))
        }
    }

    return parameter(pageParameter(::maxPage)).handler {
        val currentPage = args.getOrNull(0) as Int? ?: 1
        command.sendPage(currentPage)
    }.build()
}

private fun pageParameter(maxPage: () -> Int) = ParameterBuilder.begin<Int>("page")
    .verifiedBy {
        val input = it.toIntOrNull() ?: return@verifiedBy Result.Error("'$it' is not an integer")
        val lastPage = maxPage()
        if (input in 1..lastPage) Result.Ok(input) else Result.Error("'$it' is not in range 1..$lastPage")
    }
    .optional()
    .build()

private fun <T> pageItems(all: Collection<T>, currentPage: Int, pageSize: Int): List<T> =
    if (all is List<T>) {
        all.subList((currentPage - 1) * pageSize, minOf(currentPage * pageSize, all.size))
    } else {
        all.drop((currentPage - 1) * pageSize).subList(0, minOf(pageSize, all.size))
    }
