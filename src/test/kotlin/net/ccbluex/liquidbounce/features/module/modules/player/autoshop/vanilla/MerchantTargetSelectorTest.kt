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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MerchantTargetSelectorTest {

    @Test
    fun `reachable selector stops after the nearest valid merchant`() {
        val reachChecks = mutableListOf<Int>()
        val candidates = listOf(
            candidate(id = 3, distance = 3.0),
            candidate(id = 1, distance = 1.0),
            candidate(id = 2, distance = 2.0),
        )

        val selected = MerchantTargetSelector.selectReachable(
            candidates,
            range = 4.5f,
            canRetry = { true },
        ) { merchant ->
            reachChecks += merchant
            true
        }

        assertEquals(1, selected?.entityId)
        assertEquals(listOf(1), reachChecks)
    }

    @Test
    fun `reachable selector checks the next merchant only when the nearest is unreachable`() {
        val reachChecks = mutableListOf<Int>()
        val candidates = listOf(
            candidate(id = 1, distance = 1.0),
            candidate(id = 2, distance = 2.0),
            candidate(id = 3, distance = 3.0),
        )

        val selected = MerchantTargetSelector.selectReachable(
            candidates,
            range = 4.5f,
            canRetry = { true },
        ) { merchant ->
            reachChecks += merchant
            merchant != 1
        }

        assertEquals(2, selected?.entityId)
        assertEquals(listOf(1, 2), reachChecks)
    }

    @Test
    fun `reachable selector never raytraces state range or cooldown rejects`() {
        val reachChecks = mutableListOf<Int>()
        val candidates = listOf(
            candidate(id = 1, distance = 1.0, alive = false),
            candidate(id = 2, distance = 1.1, adult = false),
            candidate(id = 3, distance = 1.2, sleeping = true),
            candidate(id = 4, distance = 4.6),
            candidate(id = 5, distance = 2.0),
            candidate(id = 6, distance = 2.5),
        )

        val selected = MerchantTargetSelector.selectReachable(
            candidates,
            range = 4.5f,
            canRetry = { it != 5 },
        ) { merchant ->
            reachChecks += merchant
            true
        }

        assertEquals(6, selected?.entityId)
        assertEquals(listOf(6), reachChecks)
    }

    @Test
    fun `visible merchant uses normal range while occluded merchant uses wall range`() {
        val candidates = listOf(
            candidate(id = 1, distance = 4.4, visible = true),
            candidate(id = 2, distance = 3.1, visible = false),
            candidate(id = 3, distance = 2.9, visible = false),
        )

        val selected = MerchantTargetSelector.select(
            candidates,
            range = 4.5f,
            wallRange = 3f,
            canRetry = { true },
        )

        assertEquals(3, selected?.entityId)
    }

    @Test
    fun `nearest eligible merchant wins by boxed distance`() {
        val candidates = listOf(
            candidate(id = 1, distance = 4.0),
            candidate(id = 2, distance = 1.5),
            candidate(id = 3, distance = 2.0),
        )

        val selected = MerchantTargetSelector.select(candidates, 4.5f, 3f) { true }

        assertEquals(2, selected?.entityId)
    }

    @Test
    fun `dead baby sleeping and out of range merchants are ignored`() {
        val candidates = listOf(
            candidate(id = 1, distance = 1.0, alive = false),
            candidate(id = 2, distance = 1.1, adult = false),
            candidate(id = 3, distance = 1.2, sleeping = true),
            candidate(id = 4, distance = 4.6),
        )

        assertNull(MerchantTargetSelector.select(candidates, 4.5f, 3f) { true })
    }

    @Test
    fun `merchant on retry cooldown is skipped immediately for the next candidate`() {
        val candidates = listOf(
            candidate(id = 1, distance = 1.0),
            candidate(id = 2, distance = 2.0),
        )

        val selected = MerchantTargetSelector.select(candidates, 4.5f, 3f) { it != 1 }

        assertEquals(2, selected?.entityId)
    }

    private fun candidate(
        id: Int,
        distance: Double,
        visible: Boolean = true,
        alive: Boolean = true,
        adult: Boolean = true,
        sleeping: Boolean = false,
    ) = MerchantTargetCandidate(
        entity = id,
        entityId = id,
        boxedDistance = distance,
        visible = visible,
        alive = alive,
        adult = adult,
        sleeping = sleeping,
    )
}
