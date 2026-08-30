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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QualityConfigurationLoaderTest {

    @Test
    fun `loads hygiene policy and classifies test before ui`() {
        val file = Files.createTempFile("source-hygiene", ".json")
        file.writeText(HYGIENE_JSON)

        val policy = QualityConfigurationLoader.loadHygiene(file)

        assertEquals(200, policy.fileLimits.productionLimit)
        assertEquals(40, policy.structuralLimits.productionMethodLines)
        assertEquals(12, policy.structuralLimits.cognitiveComplexity)
        assertEquals(SourceKind.TEST, policy.classify("src-theme/src/widget.test.ts"))
        assertEquals(SourceKind.TEST, policy.classify("src-theme/test/themeSource.mjs"))
        assertEquals(SourceKind.TEST, policy.classify("scripts/tests/test_policy.py"))
        assertEquals(SourceKind.UI, policy.classify("src-theme/src/widget.ts"))
        assertTrue("TooManyFunctions" in policy.forbiddenSuppressions)
    }

    @Test
    fun `rejects attempts to loosen fixed source hygiene limits`() {
        val file = Files.createTempFile("source-hygiene-loosened", ".json")
        file.writeText(HYGIENE_JSON.replace("\"production\": 200", "\"production\": 201"))

        assertFailsWith<IllegalArgumentException> { QualityConfigurationLoader.loadHygiene(file) }
    }

    @Test
    fun `rejects attempts to narrow discovery or widen exclusions`() {
        val missingJava = Files.createTempFile("source-hygiene-missing-java", ".json")
        missingJava.writeText(HYGIENE_JSON.replace("    \"java\",\n", ""))
        val excludedSources = Files.createTempFile("source-hygiene-excluded-sources", ".json")
        excludedSources.writeText(
            HYGIENE_JSON.replace(
                "    \"src-theme/public\"",
                "    \"src-theme/public\",\n    \"src/main\"",
            ),
        )

        assertFailsWith<IllegalArgumentException> { QualityConfigurationLoader.loadHygiene(missingJava) }
        assertFailsWith<IllegalArgumentException> { QualityConfigurationLoader.loadHygiene(excludedSources) }
    }

    @Test
    fun `loads architecture components and restricted import patterns`() {
        val file = Files.createTempFile("source-architecture", ".json")
        file.writeText(ARCHITECTURE_JSON)

        val policy = QualityConfigurationLoader.loadArchitecture(file)

        assertEquals("features", policy.componentFor("net.ccbluex.liquidbounce.features.module")?.id)
        assertTrue(policy.restrictedEdges.single().permits("net.ccbluex.liquidbounce.features.ModuleCombat"))
        assertTrue(!policy.restrictedEdges.single().permits("net.ccbluex.liquidbounce.features.internal.Runtime"))
    }

    @Test
    fun `rejects attempts to replace the versioned responsibility graph`() {
        val file = Files.createTempFile("source-architecture-weakened", ".json")
        file.writeText(
            ARCHITECTURE_JSON.replace(
                "\"internalPackagePrefix\": \"net.ccbluex.liquidbounce\"",
                "\"internalPackagePrefix\": \"net.ccbluex\"",
            ),
        )

        assertFailsWith<IllegalArgumentException> { QualityConfigurationLoader.loadArchitecture(file) }
    }

    private companion object {
        val CONFIG_ROOT: Path = Path.of("..", "config").toAbsolutePath().normalize()
        val HYGIENE_JSON: String = Files.readString(CONFIG_ROOT.resolve("source-hygiene.json"))
        val ARCHITECTURE_JSON: String = Files.readString(CONFIG_ROOT.resolve("source-architecture.json"))
    }
}
