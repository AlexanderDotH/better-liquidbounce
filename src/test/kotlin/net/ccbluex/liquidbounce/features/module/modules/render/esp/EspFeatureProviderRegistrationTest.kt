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

package net.ccbluex.liquidbounce.features.module.modules.render.esp

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class EspFeatureProviderRegistrationTest {

    @Test
    fun `ESP owns its prepared player providers`() {
        val source = readSource("features/module/modules/render/esp/ModuleESP.kt")

        assertRegistration(source, "registerGlow", "EspGlowSource.PLAYER_ESP")
        assertRegistration(source, "registerOutline", "EspMaskLayer.PLAYER_OUTLINE")
        assertRegistration(source, "registerChams", "EspMaskLayer.ENTITY_CHAMS")
    }

    @Test
    fun `StorageESP owns its prepared and cached storage providers`() {
        val source = readSource("features/module/modules/render/ModuleStorageESP.kt")

        assertRegistration(source, "registerGlow", "EspGlowSource.STORAGE_ESP")
        assertRegistration(source, "registerOutline", "EspMaskLayer.STORAGE_OUTLINE")
        assertRegistration(source, "registerChams", "EspMaskLayer.STORAGE_CHAMS")
    }

    private fun assertRegistration(source: String, method: String, owner: String) {
        val call = source.substringAfter("EspFeatureRendererRegistry.$method(", missingDelimiterValue = "")
            .substringBefore(")")
        assertTrue(call.contains(owner), "$method must remain owned by $owner")
    }

    private fun readSource(relativePath: String) = Files.readString(Path.of("src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"))
}
