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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAutomaticEndSignal
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FlyAutomationProfileCompletenessTest {

    private data class CapabilitySignature(
        val horizontal: Boolean,
        val ascend: Boolean,
        val descend: Boolean,
        val landing: Boolean,
        val kind: FlyAutomationKind,
        val resource: String? = null,
        val reliableSpeed: Boolean = false,
    )

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `every top level fly mode exposes an automation profile`() {
        val missingProfiles = ModuleFly.modes.modes
            .filterNot { it is FlyAutomationProfile }
            .map { it.tag }

        assertEquals(emptyList<String>(), missingProfiles)
    }

    @Test
    fun `top level fly mode order remains stable`() {
        val expected = listOf(
            "Vanilla", "Packet", "Creative", "Jetpack", "Enderpearl", "AirWalk", "Explosion", "Fireball",
            "DetectorBypass", "Vulcan277", "Vulcan286-113", "Vulcan286-18", "Vulcan286-Teleport-18",
            "Grim2859-V", "Grim2373Jan15", "Spartan524", "Intave", "SentinelNoDown", "CubecraftDamage",
            "Sentinel20thApr", "Sentinel27thJan", "Sentinel10thMar", "Sentinel26thDec", "Sentry", "Megacraft",
            "MegacraftNoDown", "VerusB3896Damage", "VerusB3896Flat", "NcpClip", "AntiKickFly", "Hypixel",
            "HypixelFlat", "HycraftDamage",
        )

        assertEquals(expected, ModuleFly.modes.modes.map { it.name })
    }

    @Test
    fun `specialized fly modes publish their actual steering limits`() {
        val expected = mapOf(
            "Enderpearl" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS,
                resource = "Ender Pearl"),
            "Fireball" to CapabilitySignature(true, true, false, false, FlyAutomationKind.BURST,
                resource = "Fire Charge"),
            "DetectorBypass" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS,
                reliableSpeed = true),
            "Vulcan277" to CapabilitySignature(true, false, true, true, FlyAutomationKind.CONTINUOUS),
            "Vulcan286-113" to CapabilitySignature(true, false, true, true, FlyAutomationKind.CONTINUOUS),
            "Vulcan286-18" to CapabilitySignature(true, false, true, true, FlyAutomationKind.CONTINUOUS),
            "Vulcan286-Teleport-18" to CapabilitySignature(true, false, false, true, FlyAutomationKind.BURST),
            "Grim2859-V" to CapabilitySignature(true, false, true, true, FlyAutomationKind.CONTINUOUS),
            "Grim2373Jan15" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS,
                resource = "Elytra"),
            "Spartan524" to CapabilitySignature(true, false, false, false, FlyAutomationKind.CONTINUOUS,
                reliableSpeed = true),
            "Intave" to CapabilitySignature(true, true, false, false, FlyAutomationKind.CONTINUOUS,
                resource = "Placeable Block"),
            "SentinelNoDown" to CapabilitySignature(true, false, false, false, FlyAutomationKind.CONTINUOUS),
            "CubecraftDamage" to CapabilitySignature(true, true, false, false, FlyAutomationKind.BURST),
            "Sentinel20thApr" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS),
            "Sentinel27thJan" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS),
            "Sentinel10thMar" to CapabilitySignature(true, true, false, false, FlyAutomationKind.CONTINUOUS),
            "Sentinel26thDec" to CapabilitySignature(true, true, true, false, FlyAutomationKind.BURST),
            "Sentry" to CapabilitySignature(true, true, false, false, FlyAutomationKind.CONTINUOUS),
            "Megacraft" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS),
            "MegacraftNoDown" to CapabilitySignature(true, true, true, true, FlyAutomationKind.CONTINUOUS),
            "VerusB3896Damage" to CapabilitySignature(true, false, false, false, FlyAutomationKind.BURST),
            "VerusB3896Flat" to CapabilitySignature(true, false, false, true, FlyAutomationKind.CONTINUOUS),
            "NcpClip" to CapabilitySignature(true, false, true, true, FlyAutomationKind.BURST),
            "AntiKickFly" to CapabilitySignature(true, true, false, false, FlyAutomationKind.CONTINUOUS),
            "Hypixel" to CapabilitySignature(true, true, false, true, FlyAutomationKind.BURST),
            "HypixelFlat" to CapabilitySignature(true, false, false, true, FlyAutomationKind.BURST),
            "HycraftDamage" to CapabilitySignature(true, true, false, false, FlyAutomationKind.BURST),
        )

        val actual = ModuleFly.modes.modes
            .filter { it.tag in expected }
            .associate { mode ->
                val capabilities = (mode as FlyAutomationProfile).automationCapabilities
                mode.tag to CapabilitySignature(
                    horizontal = capabilities.horizontal,
                    ascend = capabilities.ascend,
                    descend = capabilities.descend,
                    landing = capabilities.landing,
                    kind = capabilities.kind,
                    resource = capabilities.resource,
                    reliableSpeed = capabilities.reliableSpeed,
                )
            }

        assertEquals(expected, actual)
    }

    @Test
    fun `automatic mode endings are consumed exactly once`() {
        val signal = FlyAutomaticEndSignal()

        signal.mark("burst completed")

        assertEquals("burst completed", signal.consume()?.reason)
        assertNull(signal.consume())
    }

}
