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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleAutoDodgeSpearIntegrationTest {

    @Test
    fun `using an item pauses projectile defense but keeps spear defense active`() {
        val availability = resolveAutoDodgeBranchAvailability(
            AutoDodgeRuntimeContext(
                blinkActive = false,
                inventoryBlocked = false,
                scaffoldBlocked = false,
                usingItem = true,
                allowWhileUsingItem = false,
                murderMysteryDisallowsProjectile = false,
                cleanupPending = false,
            )
        )

        assertFalse(availability.projectile)
        assertTrue(availability.spear)
        assertFalse(availability.cleanup)
    }

    @Test
    fun `murder mystery pauses projectile defense only`() {
        val availability = resolveAutoDodgeBranchAvailability(
            AutoDodgeRuntimeContext(
                blinkActive = false,
                inventoryBlocked = false,
                scaffoldBlocked = false,
                usingItem = false,
                allowWhileUsingItem = false,
                murderMysteryDisallowsProjectile = true,
                cleanupPending = false,
            )
        )

        assertFalse(availability.projectile)
        assertTrue(availability.spear)
    }

    @Test
    fun `blink pauses new defenses but cleanup remains active`() {
        val availability = resolveAutoDodgeBranchAvailability(
            AutoDodgeRuntimeContext(
                blinkActive = true,
                inventoryBlocked = false,
                scaffoldBlocked = false,
                usingItem = false,
                allowWhileUsingItem = false,
                murderMysteryDisallowsProjectile = false,
                cleanupPending = true,
            )
        )

        assertFalse(availability.projectile)
        assertFalse(availability.spear)
        assertTrue(availability.cleanup)
    }

    @Test
    fun `inventory and scaffold gates pause both new defense branches`() {
        listOf(
            AutoDodgeRuntimeContext(inventoryBlocked = true),
            AutoDodgeRuntimeContext(scaffoldBlocked = true),
        ).forEach { context ->
            val availability = resolveAutoDodgeBranchAvailability(context)

            assertFalse(availability.projectile)
            assertFalse(availability.spear)
        }
    }

    @Test
    fun `projectile movement takes precedence over spear movement`() {
        val projectile = DodgePlan(
            directionalInput = DirectionalInput.LEFT,
            shouldJump = true,
            yawChange = 45F,
            useTimer = false,
        )
        val spear = SpearDodgePlan(
            input = DirectionalInput.RIGHT,
            minimumClearance = 0.5,
            distanceFromAttacker = 3.0,
            useTimer = true,
        )

        val result = AutoDodgeMovementArbitrator.choose(projectile, spear)

        assertEquals(projectile, result)
    }

    @Test
    fun `simulation adapter captures exactly three post tick samples`() {
        var ticks = 0

        val result = collectSpearMovementSimulation(
            tickCount = 3,
            tick = { ticks++ },
            sample = {
                SpearMovementSample(
                    position = HorizontalPosition(ticks.toDouble(), -ticks.toDouble()),
                    colliding = ticks == 2,
                    supported = ticks != 3,
                    overVoid = ticks == 3,
                )
            },
        )

        assertEquals(3, ticks)
        assertEquals(
            listOf(
                HorizontalPosition(1.0, -1.0),
                HorizontalPosition(2.0, -2.0),
                HorizontalPosition(3.0, -3.0),
            ),
            result.samples.map(SpearMovementSample::position),
        )
        assertTrue(result.samples[1].colliding)
        assertFalse(result.samples[2].supported)
        assertTrue(result.samples[2].overVoid)
    }

    @Test
    fun `disabled module keeps handlers active only while shield cleanup is pending`() {
        assertTrue(shouldRunAutoDodgeHandlers(moduleRunning = true, shieldCleanupPending = false))
        assertTrue(shouldRunAutoDodgeHandlers(moduleRunning = false, shieldCleanupPending = true))
        assertFalse(shouldRunAutoDodgeHandlers(moduleRunning = false, shieldCleanupPending = false))
    }

    @Test
    fun `inventory adapter schedules only an exact reserved swap layout`() {
        val snapshot = SpearShieldInventorySnapshot(
            containerId = 0,
            sourceSlot = 9,
            shieldStack = "shield",
            displacedOffhandStack = "totem",
        )
        val equip = SpearShieldCommand.SwapIntoOffhand(snapshot)
        val restore = SpearShieldCommand.RestoreOffhand(snapshot)

        assertTrue(canScheduleSpearShieldInventoryCommand(equip, SpearShieldInventoryLayout.ORIGINAL, true))
        assertFalse(canScheduleSpearShieldInventoryCommand(equip, SpearShieldInventoryLayout.EQUIPPED, true))
        assertTrue(canScheduleSpearShieldInventoryCommand(restore, SpearShieldInventoryLayout.EQUIPPED, true))
        assertTrue(canScheduleSpearShieldInventoryCommand(restore, SpearShieldInventoryLayout.SHIELD_BROKEN, true))
        assertFalse(canScheduleSpearShieldInventoryCommand(restore, SpearShieldInventoryLayout.CHANGED, true))
        assertFalse(canScheduleSpearShieldInventoryCommand(restore, SpearShieldInventoryLayout.EQUIPPED, false))
    }
}
