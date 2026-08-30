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
package net.ccbluex.liquidbounce.features.module.modules.player.offhand

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TotemDamagePolicyTest {

    @Test
    fun `maximum damage stops evaluating once allowed damage is reached`() {
        val evaluated = mutableListOf<Float>()
        val damages = sequenceOf(2f, 9f, 20f).onEach(evaluated::add)

        val maximum = ExplosiveBlockDamage.maximumUntilThreshold(8f, damages)

        assertEquals(9f, maximum)
        assertEquals(listOf(2f, 9f), evaluated)
    }

    @Test
    fun `maximum damage consumes all candidates below allowed damage`() {
        val damages = sequenceOf(3f, 1f, 7f, 4f)

        assertEquals(7f, ExplosiveBlockDamage.maximumUntilThreshold(8f, damages))
    }

    @Test
    fun `totem threat and direct send side effects retain their order`() {
        assertOrdered(
            totemSource,
            "getDamageFromEntities(allowedDamage)",
            "getDamageFromBlocks(allowedDamage)",
            "FallDamage.getFallDamage()",
        )
        assertOrdered(
            totemSource,
            "if (!sendDirectly)",
            "InventoryManager.onClickOccurs()",
            "actions.forEach { it.performAction() }",
        )
        assertOrdered(
            blockDamageSource,
            "val noBedExplosion",
            "val noAnchorExplosion",
            "if (noBedExplosion && noAnchorExplosion)",
            "val excludedBlocks",
            "player.getDamageFromExplosion(",
        )
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing totem damage contract fragment")
        assertEquals(positions.sorted(), positions, "Totem damage side-effect order changed")
    }

    private companion object {
        private val totemSource = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/offhand/Totem.kt")
        )
        private val blockDamageSource = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/offhand/" +
                    "ExplosiveBlockDamage.kt"
            )
        )
    }
}
