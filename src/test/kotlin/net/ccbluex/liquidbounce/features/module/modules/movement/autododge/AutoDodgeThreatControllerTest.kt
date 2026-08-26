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

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class AutoDodgeThreatControllerTest {

    @Test
    fun `spear controller exposes detection without planning a movement response`() {
        assertThreatOnlyUpdateContract(Files.readString(SPEAR_CONTROLLER_PATH))
    }

    @Test
    fun `mace controller exposes detection without planning a movement response`() {
        assertThreatOnlyUpdateContract(Files.readString(MACE_CONTROLLER_PATH))
    }

    private fun assertThreatOnlyUpdateContract(source: String) {
        val update = bracedDeclaration(source, "fun update(")
        val threatOnlyUpdate = bracedDeclaration(source, "fun updateThreatOnly(")

        assertTrue(update.contains("updateThreatOnly("), "Movement update must delegate threat detection")
        assertTrue(threatOnlyUpdate.contains("threatDetector.update("), "Threat-only update must reuse the detector")
        assertTrue(threatOnlyUpdate.contains("resetMovement()"), "Threat-only update must preserve reset rules")
        assertTrue(threatOnlyUpdate.contains("primaryThreat"), "Threat-only update must publish the selected threat")
        assertTrue(
            RESPONSE_PLANNERS.none(threatOnlyUpdate::contains),
            "Threat-only update must not plan or execute a movement response",
        )
    }

    private fun bracedDeclaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val bodyStart = source.indexOf('{', markerIndex)
        require(bodyStart >= 0) { "Missing declaration body: $marker" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) {
                    return source.substring(markerIndex, index + 1)
                }
            }
        }
        error("Unclosed declaration: $marker")
    }

    private companion object {
        val SPEAR_CONTROLLER_PATH: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/" +
                "SpearMovementController.kt",
        )
        val MACE_CONTROLLER_PATH: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/autododge/" +
                "MaceMovementController.kt",
        )
        val RESPONSE_PLANNERS = listOf("dodgePlanner", "jukeCommitment", "teleportRuntime")
    }
}
