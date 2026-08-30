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

package net.ccbluex.liquidbounce.buildsrc.quality.architecture

import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureAnalyzerTest {

    private val policy = ArchitecturePolicy(
        internalPackagePrefix = "net.example",
        analyzedPathPrefixes = setOf("src/main/kotlin", "src/main/java"),
        components = listOf(
            ArchitectureComponent(
                id = "bootstrap",
                exactPackages = setOf("net.example"),
                packagePrefixes = setOf("net.example.bootstrap", "net.example.injection"),
                allowedDependencies = setOf("features", "foundation"),
            ),
            ArchitectureComponent(
                id = "features",
                packagePrefixes = setOf("net.example.features"),
                allowedDependencies = setOf("foundation"),
            ),
            ArchitectureComponent(
                id = "integration",
                packagePrefixes = setOf("net.example.script"),
                allowedDependencies = setOf("features", "foundation"),
            ),
            ArchitectureComponent(id = "foundation", packagePrefixes = setOf("net.example.utils")),
        ),
        restrictedEdges = listOf(
            RestrictedArchitectureEdge(
                fromComponent = "bootstrap",
                toComponent = "features",
                sourcePackagePrefixes = setOf("net.example.injection"),
                allowedImportPatterns = listOf(
                    Regex("net\\.example\\.features(?:\\.[a-z][a-z0-9]*)*\\.Module[A-Z][A-Za-z0-9]*"),
                    Regex("net\\.example\\.features\\..*(?:Bridge|Hook)"),
                ),
            ),
        ),
    )
    private val analyzer = ArchitectureAnalyzer(policy)

    @Test
    fun `allows dependencies that point down the responsibility graph`() {
        val files = listOf(
            source("net/example/features/Feature.kt", "net.example.features", "net.example.utils.Geometry"),
            source("net/example/utils/Geometry.kt", "net.example.utils"),
        )

        assertTrue(analyzer.analyze(files).none { it.ruleId == "LB-ARCH-001" })
    }

    @Test
    fun `bootstrap owns concrete module registration without becoming an inward dependency`() {
        val files = listOf(
            source("net/example/bootstrap/Modules.kt", "net.example.bootstrap", "net.example.features.ModuleFlight"),
            source("net/example/features/Feature.kt", "net.example.features", "net.example.bootstrap.Modules"),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(1, findings.size)
        assertTrue("features may not depend on bootstrap" in findings.single().message)
    }

    @Test
    fun `ignores explicitly external self contained packages`() {
        val external = source("org/example/External.kt", "org.example", "net.example.features.Feature")
        val internal = source("net/example/features/Feature.kt", "net.example.features")

        assertTrue(analyzer.analyze(listOf(external, internal)).none { "unclassified" in it.subject })
    }

    @Test
    fun `does not resolve an undeclared nested vendor package to the internal root`() {
        val files = listOf(
            declaredSource("net/example/Application.kt", "net.example", "object Application"),
            source("net/example/script/Browser.kt", "net.example.script", "net.example.vendor.Browser"),
        )

        assertTrue(analyzer.analyze(files).none { it.ruleId == "LB-ARCH-001" })
    }

    @Test
    fun `reports reverse dependencies and unclassified internal packages`() {
        val files = listOf(
            source("net/example/features/Feature.kt", "net.example.features"),
            source("net/example/utils/Geometry.kt", "net.example.utils", "net.example.features.Feature"),
            source("net/example/orphan/Unknown.kt", "net.example.orphan"),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(2, findings.size)
        assertTrue(findings.any { "foundation" in it.message && "features" in it.message })
        assertTrue(findings.any { "not assigned" in it.message })
    }

    @Test
    fun `fully qualified references cannot bypass dependency rules`() {
        val files = listOf(
            declaredSource(
                "net/example/utils/Geometry.kt",
                "net.example.utils",
                "class Geometry : net.example.features.runtime.FeatureRuntime()",
            ),
            declaredSource(
                "net/example/features/runtime/FeatureRuntime.kt",
                "net.example.features.runtime",
                "open class FeatureRuntime",
            ),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(1, findings.size)
        assertTrue("FeatureRuntime" in findings.single().subject)
    }

    @Test
    fun `splitting a forbidden dependency across files does not multiply its edge metric`() {
        val target = declaredSource(
            "net/example/features/runtime/FeatureRuntime.kt",
            "net.example.features.runtime",
            "class FeatureRuntime",
        )
        val unsplit = analyzer.analyze(
            listOf(source("net/example/utils/First.kt", "net.example.utils", "net.example.features.runtime.FeatureRuntime"), target),
        ).single { it.ruleId == "LB-ARCH-001" }
        val split = analyzer.analyze(
            listOf(
                source("net/example/utils/First.kt", "net.example.utils", "net.example.features.runtime.FeatureRuntime"),
                source("net/example/utils/Second.kt", "net.example.utils", "net.example.features.runtime.FeatureRuntime"),
                target,
            ),
        ).single { it.ruleId == "LB-ARCH-001" }

        assertEquals(1, unsplit.measuredValue)
        assertEquals(unsplit.measuredValue, split.measuredValue)
        assertEquals(unsplit.fingerprint, split.fingerprint)
        assertEquals(2, split.relatedPaths.size)
    }

    @Test
    fun `injection may use module facades and bridges but not feature internals`() {
        val files = listOf(
            source("net/example/injection/Mixin.kt", "net.example.injection", "net.example.features.ModuleFlight"),
            source("net/example/injection/Hook.kt", "net.example.injection", "net.example.features.RenderHook"),
            source("net/example/injection/Bad.kt", "net.example.injection", "net.example.features.internal.FlightRuntime"),
            source("net/example/features/Types.kt", "net.example.features"),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(1, findings.size)
        assertTrue("stable facade" in findings.single().recommendation)
    }

    @Test
    fun `annotations in a higher responsibility do not bypass the graph`() {
        val files = listOf(
            source(
                "net/example/features/Feature.kt",
                "net.example.features",
                "net.example.script.ScriptApiRequired",
                "net.example.script.ScriptRuntime",
            ),
            declaredSource(
                "net/example/script/ScriptApiRequired.kt",
                "net.example.script",
                "annotation class ScriptApiRequired",
            ),
            declaredSource(
                "net/example/script/ScriptRuntime.kt",
                "net.example.script",
                "class ScriptRuntime",
            ),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(2, findings.size)
        assertTrue(findings.any { "ScriptApiRequired" in it.subject })
        assertTrue(findings.any { "ScriptRuntime" in it.subject })
    }

    @Test
    fun `new annotations do not create implicit dependency exceptions`() {
        val files = listOf(
            source(
                "net/example/features/Feature.kt",
                "net.example.features",
                "net.example.script.UnconfiguredAnnotation",
            ),
            declaredSource(
                "net/example/script/UnconfiguredAnnotation.kt",
                "net.example.script",
                "annotation class UnconfiguredAnnotation",
            ),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(1, findings.size)
        assertTrue("UnconfiguredAnnotation" in findings.single().subject)
    }

    @Test
    fun `contract package does not bypass the top level responsibility graph`() {
        val files = listOf(
            source(
                "net/example/utils/Consumer.kt",
                "net.example.utils",
                "net.example.features.contract.FeaturePort",
                "net.example.features.runtime.FeatureRuntime",
            ),
            declaredSource(
                "net/example/features/contract/FeaturePort.kt",
                "net.example.features.contract",
                "interface FeaturePort",
            ),
            declaredSource(
                "net/example/features/runtime/FeatureRuntime.kt",
                "net.example.features.runtime",
                "class FeatureRuntime",
            ),
        )

        val findings = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-001" }

        assertEquals(2, findings.size)
        assertTrue(findings.any { "FeaturePort" in it.subject })
        assertTrue(findings.any { "FeatureRuntime" in it.subject })
    }

    private fun source(relativePath: String, packageName: String, vararg imports: String): SourceFile {
        val importBlock = imports.joinToString("\n") { "import $it" }
        val content = "package $packageName\n$importBlock\nclass Sample"
        return SourceFile("src/main/kotlin/$relativePath", content, SourceKind.PRODUCTION)
    }

    private fun declaredSource(relativePath: String, packageName: String, declaration: String): SourceFile = SourceFile(
        "src/main/kotlin/$relativePath",
        "package $packageName\n$declaration",
        SourceKind.PRODUCTION,
    )
}
