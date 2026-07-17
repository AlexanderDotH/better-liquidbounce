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

package net.ccbluex.liquidbounce.integration.backend.backends.cef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CefSwitchesTest {

    @Test
    fun `base switches keep chromium profile stable`() {
        val switches = CefSwitches.forConfiguration(disableGpuAcceleration = false)

        assertTrue("--no-proxy-server" in switches)
        assertTrue("--no-sandbox" in switches)
        assertTrue("--disable-dev-shm-usage" in switches)
        assertFalse("--disable-gpu" in switches)
    }

    @Test
    fun `disabled gpu acceleration avoids chromium gpu features`() {
        val switches = CefSwitches.forConfiguration(disableGpuAcceleration = true)

        assertTrue("--disable-gpu" in switches)
        assertTrue("--disable-gpu-compositing" in switches)
        assertTrue("--disable-gpu-rasterization" in switches)
        assertTrue("--disable-vulkan" in switches)
        assertTrue("--disable-webgpu" in switches)
        assertTrue("--disable-webgl" in switches)
        assertTrue("--use-angle=swiftshader" in switches)
        assertTrue("--use-gl=swiftshader" in switches)
        assertTrue(switches.any { it.startsWith("--disable-features=") && "Vulkan" in it })
        assertTrue(switches.any { it.startsWith("--disable-features=") && "SkiaGraphite" in it })
    }

    @Test
    fun `switches are unique`() {
        val switches = CefSwitches.forConfiguration(disableGpuAcceleration = true)

        assertEquals(switches.size, switches.toSet().size)
    }

    @Test
    fun `linux disables gpu acceleration by default`() {
        val disabled = CefSwitches.shouldDisableGpuAcceleration(
            isLinux = true,
            disableGpuAcceleration = false,
            disableDmabufRenderer = false,
            forceGpuAcceleration = false,
        )

        assertTrue(disabled)
    }

    @Test
    fun `non linux keeps gpu acceleration by default`() {
        val disabled = CefSwitches.shouldDisableGpuAcceleration(
            isLinux = false,
            disableGpuAcceleration = false,
            disableDmabufRenderer = false,
            forceGpuAcceleration = false,
        )

        assertFalse(disabled)
    }

    @Test
    fun `explicit disable wins over gpu acceleration opt in`() {
        val disabled = CefSwitches.shouldDisableGpuAcceleration(
            isLinux = true,
            disableGpuAcceleration = true,
            disableDmabufRenderer = false,
            forceGpuAcceleration = true,
        )

        assertTrue(disabled)
    }

    @Test
    fun `explicit gpu acceleration opt in keeps linux gpu path available`() {
        val disabled = CefSwitches.shouldDisableGpuAcceleration(
            isLinux = true,
            disableGpuAcceleration = false,
            disableDmabufRenderer = false,
            forceGpuAcceleration = true,
        )

        assertFalse(disabled)
    }

}
