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

package net.ccbluex.liquidbounce.features.module.modules.player.autobuff.features

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PotExecutionContractTest {

    @Test
    fun `throw execution keeps abort use restore and delay order`() {
        assertOrdered(
            executeSource,
            "if (!inGame) return",
            "useHotbarSlotOrOffhand(",
            "restoreServerRotationAfterTickSpoof()",
            "waitTicks(1)",
        )
    }

    @Test
    fun `tick spoof restore retains packet and rotation fallbacks`() {
        assertEquals(2, Regex("""restoreServerRotationAfterTickSpoof\(\)""").findAll(source).count())
        assertOrdered(
            restoreSource,
            "if (ModuleAutoBuff.Rotations.rotationTiming != ON_TICK) return",
            "network.send(MovePacketType.FULL.generatePacket().apply {",
            "yRot = player.withFixedYaw(currentRotation ?: player.rotation)",
            "xRot = currentRotation?.pitch ?: player.xRot",
        )
    }

    private fun assertOrdered(source: String, vararg fragments: String) {
        val positions = fragments.map(source::indexOf)
        assertTrue(positions.all { it >= 0 }, "Missing Pot execution contract fragment")
        assertEquals(positions.sorted(), positions, "Pot execution order changed")
    }

    private companion object {
        val source: String = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/player/autobuff/features/Pot.kt"
            )
        )
        val executeSource: String = source.substringAfter("override suspend fun execute(slot: HotbarItemSlot) {")
            .substringBefore("override fun isValidPotion")
        val restoreSource: String = source.substringAfter("private fun restoreServerRotationAfterTickSpoof() {")
            .substringBefore("override fun isValidPotion")
    }
}
