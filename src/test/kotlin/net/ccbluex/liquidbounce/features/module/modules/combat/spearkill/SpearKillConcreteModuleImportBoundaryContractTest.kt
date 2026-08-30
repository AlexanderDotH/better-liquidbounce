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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class SpearKillConcreteModuleImportBoundaryContractTest {

    @Test
    fun `SpearKill implementation packages do not import the concrete module facade`() {
        val packageSources = EXACT_PACKAGES.mapValues { (_, sourceDirectory) ->
            Files.list(sourceDirectory).use { paths ->
                paths.filter { it.isRegularFile() && it.extension == "kt" }.sorted().toList()
            }
        }
        val emptyPackages = packageSources.filterValues { it.isEmpty() }.keys

        assertTrue(emptyPackages.isEmpty(), "Expected Kotlin sources in packages: $emptyPackages")

        val violations = packageSources.flatMap { (packageName, sourcePaths) ->
            sourcePaths.flatMap { sourcePath ->
                Files.readAllLines(sourcePath).mapIndexedNotNull { index, line ->
                    line.takeIf { it.isConcreteModuleImport() }?.let {
                        "$packageName/${sourcePath.fileName}:${index + 1}: ${it.trim()}"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "SpearKill packages must depend on module-state contracts, not ModuleSpearKill:\n" +
                violations.joinToString(separator = "\n"),
        )
    }

    private fun String.isConcreteModuleImport(): Boolean {
        val importLine = trim()
        return importLine == FORBIDDEN_IMPORT ||
            importLine.startsWith("$FORBIDDEN_IMPORT.") ||
            importLine.startsWith("$FORBIDDEN_IMPORT as ")
    }

    private companion object {
        val SPEAR_KILL_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill",
        )
        val EXACT_PACKAGES = linkedMapOf(
            "root" to SPEAR_KILL_ROOT,
            "facade" to SPEAR_KILL_ROOT.resolve("facade"),
            "integration" to SPEAR_KILL_ROOT.resolve("integration"),
            "planner" to SPEAR_KILL_ROOT.resolve("planner"),
            "runtime" to SPEAR_KILL_ROOT.resolve("runtime"),
            "session" to SPEAR_KILL_ROOT.resolve("session"),
            "session.packet" to SPEAR_KILL_ROOT.resolve("session/packet"),
            "target" to SPEAR_KILL_ROOT.resolve("target"),
        )
        const val FORBIDDEN_IMPORT =
            "import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill"
    }
}
