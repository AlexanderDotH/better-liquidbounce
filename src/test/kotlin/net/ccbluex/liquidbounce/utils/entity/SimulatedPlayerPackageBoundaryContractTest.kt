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

package net.ccbluex.liquidbounce.utils.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SimulatedPlayerPackageBoundaryContractTest {

    @Test
    fun `split simulation helpers remain internal parts of the entity package`() {
        val helpers = HELPER_FILES.map { ENTITY_ROOT.resolve(it) }

        assertEquals(12, helpers.size)
        helpers.forEach { path ->
            assertTrue(Files.isRegularFile(path), "$path must remain part of the simulation split")
            assertFalse(
                path.fileName.toString().startsWith("SimulatedPlayer"),
                "$path must be named after its focused simulation responsibility",
            )
            val source = Files.readString(path)
            assertTrue("package $ENTITY_PACKAGE" in source, "$path must share the facade package")
            assertFalse("package $LEGACY_HELPER_PACKAGE" in source, "$path must not recreate the package cycle")
            assertFalse(SIMULATED_PLAYER_IMPORT in source, "$path must use package-local type resolution")
        }
    }

    @Test
    fun `production sources contain no references to the retired helper package`() {
        sourceFiles(Path.of("src/main")).forEach { path ->
            assertFalse(
                Files.readString(path).contains(LEGACY_HELPER_PACKAGE),
                "$path must not reference the retired helper package",
            )
        }
    }

    @Test
    fun `public simulated player fqcn remains unchanged`() {
        assertEquals("$ENTITY_PACKAGE.SimulatedPlayer", SimulatedPlayer::class.java.name)
    }

    private fun sourceFiles(root: Path): List<Path> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".kt") || it.toString().endsWith(".java")) }
            .toList()
    }

    private companion object {
        const val ENTITY_PACKAGE = "net.ccbluex.liquidbounce.utils.entity"
        const val LEGACY_HELPER_PACKAGE = "$ENTITY_PACKAGE.simulatedplayer"
        const val SIMULATED_PLAYER_IMPORT = "import $ENTITY_PACKAGE.SimulatedPlayer"
        val ENTITY_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/entity")
        val HELPER_FILES = listOf(
            "SimulationCollision.kt",
            "SimulationEnvironment.kt",
            "SimulationFactory.kt",
            "SimulationFluidState.kt",
            "SimulationFluidTravel.kt",
            "SimulationGlideTravel.kt",
            "SimulationGroundTravel.kt",
            "SimulationInputBehavior.kt",
            "SimulationMovement.kt",
            "SimulationSafeWalk.kt",
            "SimulationTick.kt",
            "SimulationGravity.kt",
        )
    }
}
