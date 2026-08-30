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

class PackageCycleAnalyzerTest {

    private val analyzer = ArchitectureAnalyzer(
        ArchitecturePolicy(
            internalPackagePrefix = "net.example",
            analyzedPathPrefixes = setOf("src/main/kotlin", "src/main/java"),
            components = listOf(
                ArchitectureComponent(
                    id = "features",
                    packagePrefixes = setOf("net.example.features"),
                ),
            ),
            restrictedEdges = emptyList(),
        ),
    )

    @Test
    fun `reports one deterministic finding per cyclic source package`() {
        val files = listOf(
            source(
                "net/example/features/a/A.kt",
                "net.example.features.a",
                "net.example.features.b.B",
                "net.example.features.c.C",
            ),
            source("net/example/features/b/B.kt", "net.example.features.b", "net.example.features.a.A"),
            source("net/example/features/c/C.kt", "net.example.features.c", "net.example.features.a.A"),
        )

        val cycles = cycles(files)
        val reversed = cycles(files.reversed())

        assertEquals(3, cycles.size)
        assertEquals(cycles, reversed)
        assertEquals(listOf(2, 1, 1), cycles.map { it.measuredValue })
        assertEquals(
            listOf(
                "cycle:net.example.features.a",
                "cycle:net.example.features.b",
                "cycle:net.example.features.c",
            ),
            cycles.map { it.subject },
        )
    }

    @Test
    fun `cycle findings stay scoped to one source package and its contributing files`() {
        val files = listOf(
            source(
                "net/example/features/player/reach/runtime/alpha/Alpha.kt",
                "net.example.features.player.reach.runtime.alpha",
                "net.example.features.player.reach.runtime.bravo.Bravo",
            ),
            source(
                "net/example/features/player/reach/runtime/bravo/Bravo.kt",
                "net.example.features.player.reach.runtime.bravo",
                "net.example.features.player.reach.runtime.alpha.Alpha",
            ),
            source(
                "net/example/features/player/unrelated/Unrelated.kt",
                "net.example.features.player.unrelated",
            ),
        )

        val cycles = cycles(files)

        assertEquals(2, cycles.size)
        assertEquals(
            setOf("src/main/kotlin/net/example/features/player/reach/runtime/alpha/Alpha.kt"),
            cycles.first().relatedPaths,
        )
        assertEquals(
            setOf("net.example.features.player.reach.runtime.alpha"),
            cycles.first().ratchetAliases,
        )
    }

    @Test
    fun `splitting a cyclic package across files does not multiply its edge metric`() {
        val target = source(
            "net/example/features/b/B.kt",
            "net.example.features.b",
            "net.example.features.a.A",
        )
        val unsplit = cycles(
            listOf(source("net/example/features/a/A.kt", "net.example.features.a", "net.example.features.b.B"), target),
        ).single { it.subject == "cycle:net.example.features.a" }
        val split = cycles(
            listOf(
                source("net/example/features/a/A.kt", "net.example.features.a", "net.example.features.b.B"),
                source("net/example/features/a/AHelper.kt", "net.example.features.a", "net.example.features.b.B"),
                target,
            ),
        ).single { it.subject == "cycle:net.example.features.a" }

        assertEquals(1, unsplit.measuredValue)
        assertEquals(unsplit.measuredValue, split.measuredValue)
        assertEquals(unsplit.fingerprint, split.fingerprint)
        assertEquals(2, split.relatedPaths.size)
    }

    @Test
    fun `dense cycle feedback avoids per edge explosion`() {
        val packages = (1..40).map { "net.example.features.package$it" }
        val files = packages.mapIndexed { index, packageName ->
            val next = packages[(index + 1) % packages.size]
            val previous = packages[(index - 1 + packages.size) % packages.size]
            source("${packageName.replace('.', '/')}/Type.kt", packageName, "$next.Type", "$previous.Type")
        }

        val cycles = cycles(files)

        assertEquals(40, cycles.size)
        assertTrue(cycles.all { it.measuredValue == 2 })
        assertTrue(cycles.all { it.message.length < 300 })
        assertTrue(cycles.all { it.subject.removePrefix("cycle:") in it.message })
    }

    @Test
    fun `homogeneous strategy packages still reject package cycles`() {
        val files = listOf(
            source(
                "net/example/features/fly/modes/alpha/Alpha.kt",
                "net.example.features.fly.modes.alpha",
                "net.example.features.fly.modes.bravo.Bravo",
            ),
            source(
                "net/example/features/fly/modes/bravo/Bravo.kt",
                "net.example.features.fly.modes.bravo",
                "net.example.features.fly.modes.alpha.Alpha",
            ),
        )

        assertEquals(
            listOf(
                "cycle:net.example.features.fly.modes.alpha",
                "cycle:net.example.features.fly.modes.bravo",
            ),
            cycles(files).map { it.subject },
        )
    }

    @Test
    fun `cross role and cross feature back edges remain hard cycles`() {
        val files = listOf(
            source(
                "net/example/features/fly/runtime/FlyRuntime.kt",
                "net.example.features.fly.runtime",
                "net.example.features.fly.policy.FlyPolicy",
                "net.example.features.speed.policy.SpeedPolicy",
            ),
            source(
                "net/example/features/fly/policy/FlyPolicy.kt",
                "net.example.features.fly.policy",
                "net.example.features.fly.runtime.FlyRuntime",
            ),
            source(
                "net/example/features/speed/policy/SpeedPolicy.kt",
                "net.example.features.speed.policy",
                "net.example.features.fly.runtime.FlyRuntime",
            ),
        )

        assertEquals(
            listOf(
                "cycle:net.example.features.fly.policy",
                "cycle:net.example.features.fly.runtime",
                "cycle:net.example.features.speed.policy",
            ),
            cycles(files).map { it.subject },
        )
    }

    private fun cycles(files: List<SourceFile>) = analyzer.analyze(files).filter { it.ruleId == "LB-ARCH-002" }

    private fun source(relativePath: String, packageName: String, vararg imports: String): SourceFile {
        val importBlock = imports.joinToString("\n") { "import $it" }
        val content = "package $packageName\n$importBlock\nclass Sample"
        return SourceFile("src/main/kotlin/$relativePath", content, SourceKind.PRODUCTION)
    }
}
