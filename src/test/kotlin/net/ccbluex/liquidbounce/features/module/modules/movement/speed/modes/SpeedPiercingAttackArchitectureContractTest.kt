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

package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SpeedPiercingAttackArchitectureContractTest {

    @Test
    fun `piercing attack ranks spears locally without depending on inventory cleaner`() {
        val source = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/" +
                    "speed/modes/SpeedGeneric.kt",
            ),
        )

        assertFalse(source.contains("features.module.modules.player.invcleaner.items"))
        assertTrue(
            source.contains(
                "private val COMPARING_LUNGE_AND_SPEED = " +
                    "comparingEnchantmentLevel(Enchantments.LUNGE).asHolderComparator()",
            ),
        )
        assertTrue(source.contains(".thenComparingDouble { it.itemStack.attackSpeed }"))
    }

}
