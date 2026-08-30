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
package net.ccbluex.liquidbounce.utils.network

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class ClickBlockWithSlotOrderContractTest {

    @Test
    fun `slot policy packet use swing restore and visual reset stay ordered`() {
        val source = Files.readString(Path.of(SOURCE))
        val click = declaration(source, "fun LocalPlayer.clickBlockWithSlot(")

        assertInOrder(
            click,
            "val prevHotbarSlot = this.inventory.selectedSlot",
            "selectBlockSlotForUse(slot, hand, switchPolicy)",
            "ServerboundUseItemOnPacket",
            "useItemOnBlock(slot, itemUsageContext)",
            "actionResult.shouldSwingHand()",
            "switchPolicy.shouldRestoreServerSlot(slotChanged, hand == InteractionHand.MAIN_HAND)",
            "connection.sendHeldItemChange(prevHotbarSlot)",
            "this.inventory.selectedSlot = prevHotbarSlot",
        )
    }

    @Test
    fun `main-hand slot selection checks policy before local and server selection`() {
        val source = Files.readString(Path.of(SOURCE))
        val selection = declaration(source, "private fun LocalPlayer.selectBlockSlotForUse(")

        assertInOrder(
            selection,
            "if (hand != InteractionHand.MAIN_HAND)",
            "switchPolicy.canUseSlot(slotChanged)",
            "this.inventory.selectedSlot = slot",
            "connection.sendHeldItemChange(slot)",
        )
    }

    @Test
    fun `creative item use restores the stack count before returning its result`() {
        val source = Files.readString(Path.of(SOURCE))
        val use = declaration(source, "private fun LocalPlayer.useItemOnBlock(")

        assertInOrder(
            use,
            "val itemStack = this.inventory.getItem(slot)",
            "if (this.isCreative)",
            "val count = itemStack.count",
            "val actionResult = itemStack.useOn(itemUsageContext)",
            "itemStack.count = count",
            "return actionResult",
            "return itemStack.useOn(itemUsageContext)",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun declaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        require(openingBrace >= 0) { "Missing declaration body: $marker" }
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration: $marker")
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/network/Send1_21_5StartSneaking.kt"
    }
}
