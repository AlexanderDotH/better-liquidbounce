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
package net.ccbluex.liquidbounce.utils.item

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class ItemInteractableClassificationTest {

    @Test
    fun `classification delegates independent interaction responsibilities`() {
        val source = Files.readString(SOURCE)
        val declaration = source.substringAfter("fun ItemStack.isInteractable(): Boolean {")
            .substringBefore("private fun ItemStack.isEquippableInteraction")

        assertTrue("isEquippableInteraction()" in declaration)
        assertTrue("hasInteractionComponent()" in declaration)
        assertTrue("isDirectUseInteraction()" in declaration)
        assertTrue("isUseOnInteraction()" in declaration)
    }

    @Test
    fun `known use item families remain represented after extraction`() {
        val source = Files.readString(SOURCE)
        val expectedTypes = listOf(
            "ArmorStandItem", "BoatItem", "BlockItem", "BottleItem", "BowItem", "BrushItem",
            "BucketItem", "CrossbowItem", "EggItem", "EmptyMapItem", "EnderEyeItem", "EnderpearlItem",
            "ExperienceBottleItem", "FireChargeItem", "FireworkRocketItem", "FishingRodItem",
            "FlintAndSteelItem", "HangingEntityItem", "InstrumentItem", "KnowledgeBookItem",
            "PlaceOnWaterBlockItem", "PotionItem", "SnowballItem", "SpawnEggItem", "SpyglassItem",
            "TridentItem", "WindChargeItem", "WritableBookItem", "WrittenBookItem",
        )

        expectedTypes.forEach { type -> assertTrue(Regex("""\bis $type\b""").containsMatchIn(source), type) }
        assertTrue("Slots.All.any { it.itemStack.item is ArrowItem }" in source)
        assertTrue("player.handItems.any { it.item is FireworkRocketItem }" in source)
    }

    @Test
    fun `direct use classification groups passive families after ranged item checks`() {
        val source = Files.readString(SOURCE)
        val declaration = source.substringAfter("private fun ItemStack.isDirectUseInteraction(): Boolean")
            .substringBefore("private fun ItemStack.isUseOnInteraction")

        assertTrue("when (item)" in declaration)
        assertInOrder(
            declaration,
            "is BowItem ->",
            "Slots.All.any { it.itemStack.item is ArrowItem }",
            "is CrossbowItem ->",
            "player.handItems.any { it.item is FireworkRocketItem }",
            "is BoatItem,",
            "is WrittenBookItem -> true",
            "else -> false",
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
        val SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/utils/item/GetDestroySpeedWithEnchantment.kt"
        )
    }
}
