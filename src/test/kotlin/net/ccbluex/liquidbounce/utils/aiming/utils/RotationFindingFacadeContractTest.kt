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

package net.ccbluex.liquidbounce.utils.aiming.utils

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotationFindingFacadeContractTest {

    @Test
    fun `split rotation finding sources retain the shared JVM facade`() {
        rotationFindingSources.forEach { (fileName, source) ->
            assertTrue("@file:JvmName(\"RotationFindingKt\")" in source, "$fileName must retain the JVM facade")
            assertTrue("@file:JvmMultifileClass" in source, "$fileName must remain in the multifile facade")
            assertTrue("package $ROTATION_FINDING_PACKAGE" in source, "$fileName must remain in the package")
        }
    }

    @Test
    fun `rotation finding sources have no kotlin utility edge or nested-depth suppression`() {
        rotationFindingSources.forEach { (fileName, source) ->
            assertFalse(
                "import net.ccbluex.liquidbounce.utils.kotlin." in source,
                "$fileName must not recreate the utils.kotlin package edge",
            )
        }

        assertFalse(
            "NestedBlockDepth" in rotationFindingSources.getValue("VisibilityPredicate.kt"),
            "InterfaceVisibilityPredicate must express its shallow responsibility without suppression",
        )
    }

    @Test
    fun `projection and candidate traversal retain named responsibilities`() {
        val pointFinding = Files.readString(SOURCE_ROOT.resolve("PointFinding.kt"))
        val raytraceBoxes = rotationFindingSources.getValue("RaytraceBoxes.kt")

        assertTrue("createProjectedBoxSection(" in pointFinding)
        assertTrue("visitBoxCandidateSpots(" in raytraceBoxes)
    }

    private companion object {
        const val ROTATION_FINDING_PACKAGE = "net.ccbluex.liquidbounce.utils.aiming.utils"
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming/utils")
        val rotationFindingSources: Map<String, String> = listOf(
            "BlockRotationFinding.kt",
            "CanSeeBox.kt",
            "VisibilityPredicate.kt",
            "RaytraceBoxes.kt",
        ).associateWith { Files.readString(SOURCE_ROOT.resolve(it)) }
    }
}
