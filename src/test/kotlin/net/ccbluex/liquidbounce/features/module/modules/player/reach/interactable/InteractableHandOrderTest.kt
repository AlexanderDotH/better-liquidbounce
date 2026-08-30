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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.interactWithVanillaHandOrder
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableHandOrderTest {

    @Test
    fun `main-hand pass tries offhand without an unrelated item-use fallback`() {
        val hands = mutableListOf<InteractionHand>()

        val committed = interactWithVanillaHandOrder { hand ->
            hands += hand
            if (hand == InteractionHand.MAIN_HAND) InteractionResult.PASS else InteractionResult.SUCCESS
        }

        assertTrue(committed)
        assertEquals(listOf(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND), hands)
    }

    @Test
    fun `committed main-hand interaction stops before offhand`() {
        val hands = mutableListOf<InteractionHand>()

        val committed = interactWithVanillaHandOrder { hand ->
            hands += hand
            InteractionResult.SUCCESS
        }

        assertTrue(committed)
        assertEquals(listOf(InteractionHand.MAIN_HAND), hands)
    }

    @Test
    fun `failed main-hand interaction remains committed and stops before offhand`() {
        val hands = mutableListOf<InteractionHand>()

        val committed = interactWithVanillaHandOrder { hand ->
            hands += hand
            InteractionResult.FAIL
        }

        assertTrue(committed)
        assertEquals(listOf(InteractionHand.MAIN_HAND), hands)
    }
}
