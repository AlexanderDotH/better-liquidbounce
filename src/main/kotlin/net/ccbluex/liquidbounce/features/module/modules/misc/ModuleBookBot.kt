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

package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.bookbot.BookContentBuilder
import net.ccbluex.liquidbounce.features.module.modules.misc.bookbot.GenerationMode
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryAction
import net.ccbluex.liquidbounce.features.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * ModuleBookBot
 *
 * This module simplifies the process of filling and creating books using various principles,
 * enabling efficient generation and potential automation for mass book creation or "spam."
 *
 * @author sqlerrorthing
 * @since 12/28/2024
 **/
object ModuleBookBot : ClientModule("BookBot", ModuleCategories.EXPLOIT, disableOnQuit = true) {
    private val inventoryConstraints = tree(PlayerInventoryConstraints())

    internal val generationMode = choices(
        "Mode",
        GenerationMode.Random,
        arrayOf(GenerationMode.Random, GenerationMode.File)
    ).apply {
        tagBy(this)
    }

    private object Sign : ToggleableValueGroup(ModuleBookBot, "Sign", true) {
        val bookName by text("Name", "Generated book #%count%")
    }

    init {
        treeAll(Sign)
    }

    private val delay by float("Delay", .5f, 0f..20f, suffix = "s")

    private val chronometer = Chronometer()

    private var bookCount = 0

    override fun onEnabled() {
        bookCount = 0
        chronometer.reset()
    }

    private fun isCandidate(itemStack: ItemStack): Boolean {
        return itemStack.item == Items.WRITABLE_BOOK &&
            itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT)?.pages()?.isEmpty() == true
    }

    private val randomBook get() = Slots.All.findSlot(::isCandidate)

    @Suppress("unused")
    private val scheduleInventoryAction = handler<ScheduleInventoryActionEvent> { event ->
        val book = randomBook ?: run {
            enabled = false
            return@handler
        }

        if (!isCandidate(player.mainHandItem)) {
            event.schedule(
                inventoryConstraints, InventoryAction.Click.performSwap(
                from = book,
                to = HotbarItemSlot(player.inventory.selectedSlot),
            ))
        }

        if (chronometer.hasElapsed((delay * 1000L).toLong())) {
            chronometer.reset()
            writeBook()
        }
    }

    /**
     * Generates a book with content based on the active choice of the generation mode.
     * The book content is generated character by character, and the text is split into pages,
     * ensuring that each page contains lines that fit within the given width constraints.
     *
     * This method processes each character from the generator, managing line breaks and page formatting,
     * and stores the generated text in the `pages` and `filteredPages` lists. Once a page is full, it is
     * added to the collection, and the process continues until the specified number of pages is reached.
     *
     * The method performs the following steps:
     * - Generates characters using the active choice from the generation mode.
     * - Breaks lines based on a width limit and ensures that a line fits within this constraint.
     * - Adds new lines when a line exceeds the width limit or encounters a line break character (`\r` or `\n`).
     * - If a page is full, it is added to the `pages` and `filteredPages` lists, and the process continues.
     * - Stops once the desired number of pages is generated.
     *
     * The generated pages are used to create a book with the specified name, which is then saved.
     *
     *
     * @see PrimitiveIterator.OfInt
     * @see GenerationMode.generate
     */
    private fun writeBook() {
        if (!isCandidate(player.mainHandItem)) {
            return
        }

        val title = Sign.bookName.replace("%count%", bookCount.toString())
        val bookBuilder = BookContentBuilder(title, generationMode.activeMode.pages)
        val generator = generationMode.activeMode.generate()
            .filter { it.toChar() != '\r' }
            .iterator()

        bookBuilder.buildBookContent(generator) {
            mc.font.splitter.widthProvider.getWidth(it, Style.EMPTY)
        }
        bookBuilder.writeBook(
            stack = player.mainHandItem,
            author = player.gameProfile.name,
            selectedSlot = player.inventory.selectedSlot,
            sign = Sign.enabled,
            send = player.connection::send,
        )

        bookCount++
    }

}
