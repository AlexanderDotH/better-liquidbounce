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

package net.ccbluex.liquidbounce.features.module.modules.player

import net.minecraft.world.entity.player.Abilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoCapabilityFlightControllerTest {

    @Test
    fun `activation removes current flight capability without changing other abilities`() {
        val abilities = abilities(mayFly = true, flying = true).apply {
            invulnerable = true
            instabuild = true
            setFlyingSpeed(0.2f)
            setWalkingSpeed(0.3f)
        }
        val controller = NoCapabilityFlightController()

        controller.activate(abilities)

        assertFalse(abilities.mayfly)
        assertFalse(abilities.flying)
        assertTrue(abilities.invulnerable)
        assertTrue(abilities.instabuild)
        assertEquals(0.2f, abilities.flyingSpeed)
        assertEquals(0.3f, abilities.walkingSpeed)

        controller.deactivate(abilities)

        assertTrue(abilities.mayfly)
        assertTrue(abilities.flying)
    }

    @Test
    fun `latest server flight state is suppressed and restored on deactivation`() {
        val abilities = abilities(mayFly = false, flying = false)
        val controller = NoCapabilityFlightController()
        controller.activate(abilities)

        abilities.mayfly = true
        abilities.flying = false
        abilities.invulnerable = true
        abilities.instabuild = true
        abilities.flyingSpeed = 0.25f
        abilities.walkingSpeed = 0.35f
        controller.onServerAbilitiesApplied(
            FlightCapabilityState(mayFly = true, flying = false),
            abilities,
        )

        assertFalse(abilities.mayfly)
        assertFalse(abilities.flying)
        assertTrue(abilities.invulnerable)
        assertTrue(abilities.instabuild)
        assertEquals(0.25f, abilities.flyingSpeed)
        assertEquals(0.35f, abilities.walkingSpeed)

        controller.deactivate(abilities)

        assertTrue(abilities.mayfly)
        assertFalse(abilities.flying)
    }

    @Test
    fun `server revocation replaces an earlier grant`() {
        val abilities = abilities(mayFly = false, flying = false)
        val controller = NoCapabilityFlightController()
        controller.activate(abilities)

        controller.onServerAbilitiesApplied(FlightCapabilityState(mayFly = true, flying = true), abilities)
        controller.onServerAbilitiesApplied(FlightCapabilityState(mayFly = false, flying = false), abilities)
        controller.deactivate(abilities)

        assertFalse(abilities.mayfly)
        assertFalse(abilities.flying)
    }

    @Test
    fun `session reset prevents stale capability restoration`() {
        val abilities = abilities(mayFly = true, flying = true)
        val controller = NoCapabilityFlightController()
        controller.activate(abilities)

        controller.reset()
        abilities.mayfly = false
        abilities.flying = false
        controller.deactivate(abilities)

        assertFalse(abilities.mayfly)
        assertFalse(abilities.flying)
    }

    private fun abilities(mayFly: Boolean, flying: Boolean) = Abilities().apply {
        mayfly = mayFly
        this.flying = flying
    }
}
