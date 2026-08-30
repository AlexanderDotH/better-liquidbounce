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

package net.ccbluex.liquidbounce.features.module.modules.misc.middleclick

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiddleClickActionRuntimeBridgeTest {

    @Test
    fun `active mode decision is delegated without changing mode identity`() {
        val expectedMode = object : Mode("Expected") { }
        val otherMode = object : Mode("Other") { }
        MiddleClickActionRuntimeBridge.install(object : MiddleClickActionRuntime {
            override fun isActive(mode: Mode) = mode === expectedMode
            override fun findPlayerInCrosshair(pickUpRange: Float): Player? = null
            override fun toggleFriend(entity: Player) = Unit
            override fun setAmnesiaTarget(entity: Player) = false
            override fun selectNukerBlock(hitResult: BlockHitResult) = false
        })

        assertTrue(MiddleClickActionRuntimeBridge.isActive(expectedMode))
        assertFalse(MiddleClickActionRuntimeBridge.isActive(otherMode))
    }
}
