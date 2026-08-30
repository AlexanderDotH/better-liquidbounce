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

import kotlin.test.Test
import kotlin.test.assertEquals

class MiddleClickSmartResolverTest {

    @Test
    fun `player prefers acquired amnesia target over acquired friend`() {
        val input = smartInput(
            target = MiddleClickSmartTarget.PLAYER,
            friendTargetAcquired = true,
            amnesiaRunning = true,
            amnesiaTargetAcquired = true,
        )

        assertEquals(MiddleClickSmartAction.AMNESIA_TARGET, MiddleClickSmartResolver.resolve(input))
    }

    @Test
    fun `player falls back to friend for every missing amnesia prerequisite`() {
        val missingAmnesiaPrerequisite = listOf(
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                options = allOptions.copy(amnesiaTarget = false),
                friendTargetAcquired = true,
                amnesiaRunning = true,
                amnesiaTargetAcquired = true,
            ),
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                friendTargetAcquired = true,
                amnesiaRunning = false,
                amnesiaTargetAcquired = true,
            ),
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                friendTargetAcquired = true,
                amnesiaRunning = true,
                amnesiaTargetAcquired = false,
            ),
        )

        missingAmnesiaPrerequisite.forEach { input ->
            assertEquals(MiddleClickSmartAction.FRIEND_CLICKER, MiddleClickSmartResolver.resolve(input))
        }
    }

    @Test
    fun `player requires enabled and acquired friend target`() {
        val unavailableFriendTargets = listOf(
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                options = allOptions.copy(friendClicker = false, amnesiaTarget = false),
                friendTargetAcquired = true,
            ),
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                options = allOptions.copy(amnesiaTarget = false),
                friendTargetAcquired = false,
            ),
        )

        unavailableFriendTargets.forEach { input ->
            assertEquals(MiddleClickSmartAction.NONE, MiddleClickSmartResolver.resolve(input))
        }
    }

    @Test
    fun `player never falls through to air actions`() {
        val options = allOptions.copy(friendClicker = false, amnesiaTarget = false)
        val airActionInputs = listOf(
            smartInput(target = MiddleClickSmartTarget.PLAYER, options = options),
            smartInput(
                target = MiddleClickSmartTarget.PLAYER,
                options = options,
                vClipRunning = true,
            ),
        )

        airActionInputs.forEach { input ->
            assertEquals(MiddleClickSmartAction.NONE, MiddleClickSmartResolver.resolve(input))
        }
    }

    @Test
    fun `block prefers nuker over held vclip when both are available`() {
        val input = smartInput(
            target = MiddleClickSmartTarget.BLOCK,
            nukerRunning = true,
            vClipRunning = true,
        )

        assertEquals(MiddleClickSmartAction.NUKER_BLOCK, MiddleClickSmartResolver.resolve(input))
    }

    @Test
    fun `block falls back to held vclip when nuker option or module is disabled`() {
        val unavailableNukerInputs = listOf(
            smartInput(
                target = MiddleClickSmartTarget.BLOCK,
                options = allOptions.copy(nukerBlock = false),
                nukerRunning = true,
                vClipRunning = true,
            ),
            smartInput(
                target = MiddleClickSmartTarget.BLOCK,
                nukerRunning = false,
                vClipRunning = true,
            ),
        )

        unavailableNukerInputs.forEach { input ->
            assertEquals(MiddleClickSmartAction.VCLIP_HOLD, MiddleClickSmartResolver.resolve(input))
        }
    }

    @Test
    fun `block never falls through to pearl when neither nuker nor vclip is available`() {
        val input = smartInput(
            target = MiddleClickSmartTarget.BLOCK,
            nukerRunning = false,
            vClipRunning = false,
        )

        assertEquals(MiddleClickSmartAction.NONE, MiddleClickSmartResolver.resolve(input))
    }

    @Test
    fun `air starts held vclip modifier when lock and vclip are available`() {
        val input = smartInput(
            target = MiddleClickSmartTarget.AIR,
            vClipRunning = true,
        )

        assertEquals(MiddleClickSmartAction.VCLIP_HOLD, MiddleClickSmartResolver.resolve(input))
    }

    @Test
    fun `air falls back to pearl only when vclip lock is unavailable`() {
        val unavailableLockInputs = listOf(
            smartInput(
                target = MiddleClickSmartTarget.AIR,
                options = allOptions.copy(vClipLock = false),
                vClipRunning = true,
            ),
            smartInput(
                target = MiddleClickSmartTarget.AIR,
                vClipRunning = false,
            ),
        )

        unavailableLockInputs.forEach { input ->
            assertEquals(MiddleClickSmartAction.PEARL, MiddleClickSmartResolver.resolve(input))
        }
    }

    @Test
    fun `air with no available action resolves to none`() {
        val input = smartInput(
            target = MiddleClickSmartTarget.AIR,
            options = allOptions.copy(pearl = false),
        )

        assertEquals(MiddleClickSmartAction.NONE, MiddleClickSmartResolver.resolve(input))
    }

    private fun smartInput(
        target: MiddleClickSmartTarget,
        options: MiddleClickSmartOptions = allOptions,
        friendTargetAcquired: Boolean = false,
        amnesiaRunning: Boolean = false,
        amnesiaTargetAcquired: Boolean = false,
        nukerRunning: Boolean = false,
        vClipRunning: Boolean = false,
    ) = MiddleClickSmartInput(
        target = target,
        options = options,
        friendTargetAcquired = friendTargetAcquired,
        amnesiaRunning = amnesiaRunning,
        amnesiaTargetAcquired = amnesiaTargetAcquired,
        nukerRunning = nukerRunning,
        vClipRunning = vClipRunning,
    )

    private companion object {
        val allOptions = MiddleClickSmartOptions(
            friendClicker = true,
            pearl = true,
            amnesiaTarget = true,
            nukerBlock = true,
            vClipLock = true,
        )
    }
}
