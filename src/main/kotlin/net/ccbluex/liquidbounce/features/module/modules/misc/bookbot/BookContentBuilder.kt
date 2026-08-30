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

package net.ccbluex.liquidbounce.features.module.modules.misc.bookbot

import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundEditBookPacket
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.WrittenBookContent
import java.util.Optional
import java.util.PrimitiveIterator

private const val MAX_LINES_PER_PAGE = 14
private const val MAX_LINE_WIDTH = 114f

internal class BookContentBuilder(private val title: String, private val pageAmount: Int) {
    private val pages = ArrayList<String>(pageAmount)
    private val filteredPages = ArrayList<Filterable<Component>>(pageAmount)
    private var pageIndex = 0
    private var lineIndex = 0
    private var lineWidth = 0f
    private val page = StringBuilder()

    fun buildBookContent(charGenerator: PrimitiveIterator.OfInt, charWidthProvider: (Int) -> Float) {
        while (charGenerator.hasNext() && pageIndex < pageAmount) {
            val char = charGenerator.nextInt().toChar()
            if (!appendCharacter(char, charWidthProvider)) continue
            if (lineIndex == MAX_LINES_PER_PAGE) finishPage(char)
        }
        if (page.isNotEmpty() && pageIndex < pageAmount) addPage(page.toString())
    }

    private fun appendCharacter(char: Char, widthProvider: (Int) -> Float): Boolean {
        if (lineWidth == 0f && char == ' ') return false
        if (char == '\r' || char == '\n') {
            page.append('\n')
            lineWidth = 0f
            lineIndex++
            return true
        }
        val width = widthProvider(char.code)
        if (lineWidth + width > MAX_LINE_WIDTH) {
            lineIndex++
            lineWidth = width
            page.appendLineBreak(lineIndex)
        } else {
            lineWidth += width
            page.appendCodePoint(char.code)
        }
        return true
    }

    private fun finishPage(lastCharacter: Char) {
        addPage(page.toString())
        page.setLength(0)
        pageIndex++
        lineIndex = 0
        if (pageIndex < pageAmount && lastCharacter != '\r' && lastCharacter != '\n') page.append(lastCharacter)
    }

    private fun StringBuilder.appendLineBreak(index: Int) {
        append('\n')
        if (index == MAX_LINES_PER_PAGE) append(' ')
    }

    private fun addPage(content: String) {
        filteredPages.add(Filterable.passThrough(content.asPlainText()))
        pages.add(content)
    }

    fun writeBook(
        stack: ItemStack,
        author: String,
        selectedSlot: Int,
        sign: Boolean,
        send: (Packet<*>) -> Unit,
    ) {
        stack.set(
            DataComponents.WRITTEN_BOOK_CONTENT,
            WrittenBookContent(Filterable.passThrough(title), author, 0, filteredPages, true),
        )
        send(ServerboundEditBookPacket(selectedSlot, pages, if (sign) Optional.of(title) else Optional.empty()))
    }
}
