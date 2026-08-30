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

package net.ccbluex.liquidbounce.common.attack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcceptedAttackHookTest {

    @Test
    fun `attack without an installed handler is not applied`() {
        val commit = AcceptedAttackCommit<Any, Any>()

        assertEquals(AcceptedAttackResult.NOT_APPLIED, commit.commit(Any(), Any()))
    }

    @Test
    fun `commit forwards the accepted player and target to its single owner`() {
        val commit = AcceptedAttackCommit<Any, Any>()
        val player = Any()
        val target = Any()
        var applications = 0
        commit.install { receivedPlayer, receivedTarget ->
            assertTrue(receivedPlayer === player)
            assertTrue(receivedTarget === target)
            applications++
            AcceptedAttackResult.APPLIED
        }

        val result = commit.commit(player, target)

        assertEquals(AcceptedAttackResult.APPLIED, result)
        assertEquals(1, applications)
    }

    @Test
    fun `only a rejected preparation prevents the vanilla attack`() {
        assertFalse(AcceptedAttackResult.REJECTED.allowsAttack)
        assertTrue(AcceptedAttackResult.APPLIED.allowsAttack)
        assertTrue(AcceptedAttackResult.NOT_APPLIED.allowsAttack)
    }

    @Test
    fun `second handler cannot replace the accepted attack owner`() {
        val commit = AcceptedAttackCommit<Any, Any>()
        commit.install { _, _ -> AcceptedAttackResult.APPLIED }

        assertThrows(IllegalStateException::class.java) {
            commit.install { _, _ -> AcceptedAttackResult.NOT_APPLIED }
        }
    }
}
