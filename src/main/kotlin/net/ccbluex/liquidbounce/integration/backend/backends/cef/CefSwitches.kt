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

internal object CefSwitches {

    private val baseSwitches = listOf(
        "--no-proxy-server",
        // Avoid Chromium profile lock failures on Linux (especially with spaced paths).
        "--no-sandbox",
        "--disable-dev-shm-usage",
    )

    private val softwareRenderingSwitches = listOf(
        "--disable-gpu",
        "--disable-gpu-compositing",
        "--disable-gpu-rasterization",
        "--disable-oop-rasterization",
        "--disable-zero-copy",
        "--disable-3d-apis",
        "--disable-accelerated-2d-canvas",
        "--disable-accelerated-video-decode",
        "--disable-vulkan",
        "--disable-webgl",
        "--disable-webgpu",
        "--use-angle=swiftshader",
        "--use-gl=swiftshader",
        "--disable-features=CanvasOopRasterization,DefaultANGLEVulkan,SkiaGraphite,Vulkan,VulkanFromANGLE," +
            "VaapiVideoDecoder,VaapiVideoEncoder",
    )

    fun forConfiguration(disableGpuAcceleration: Boolean): List<String> {
        if (!disableGpuAcceleration) {
            return baseSwitches
        }

        return baseSwitches + softwareRenderingSwitches
    }

    fun shouldDisableGpuAcceleration(
        isLinux: Boolean,
        disableGpuAcceleration: Boolean,
        disableDmabufRenderer: Boolean,
        forceGpuAcceleration: Boolean,
    ): Boolean {
        if (disableGpuAcceleration || disableDmabufRenderer) {
            return true
        }

        if (forceGpuAcceleration) {
            return false
        }

        return isLinux
    }

}
