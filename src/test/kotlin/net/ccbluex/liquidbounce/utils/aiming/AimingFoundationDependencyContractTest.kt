/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.utils.aiming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class AimingFoundationDependencyContractTest {

    @Test
    fun `aiming foundation has no upward dependencies`() {
        val violations = Files.walk(AIMING_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == "kt" }
                .flatMap { path -> forbiddenImports(path).stream() }
                .sorted()
                .toList()
        }

        assertTrue(violations.isEmpty(), violations.joinToString(separator = "\n"))
    }

    @Test
    fun `projectile diagnostics preserve the projectile aimbot debug identity`() {
        assertEquals("ModuleProjectileAimbot", ProjectileAimingDebugOwner.debugOwnerId)
        assertEquals("ProjectileAimbot", ProjectileAimingDebugOwner.debugDisplayName.string)
    }

    private fun forbiddenImports(path: Path): List<String> = Files.readAllLines(path)
        .filter { line -> FORBIDDEN_IMPORT_PREFIXES.any(line::startsWith) }
        .map { line -> "$path: $line" }

    private companion object {
        val AIMING_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/aiming")

        val FORBIDDEN_IMPORT_PREFIXES = listOf(
            "import net.ccbluex.liquidbounce.config",
            "import net.ccbluex.liquidbounce.event",
            "import net.ccbluex.liquidbounce.features",
            "import net.ccbluex.liquidbounce.injection",
            "import net.ccbluex.liquidbounce.render",
        )
    }
}
