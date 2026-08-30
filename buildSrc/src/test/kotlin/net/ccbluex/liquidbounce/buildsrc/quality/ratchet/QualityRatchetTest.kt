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

package net.ccbluex.liquidbounce.buildsrc.quality.ratchet

import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityRatchetTest {

    @Test
    fun `unchanged untouched debt is tolerated and reduced debt passes`() {
        val baseline = baseline(entry("first", 250), entry("second", 9))
        val findings = listOf(finding("first", 250), finding("second", 4))

        val result = QualityRatchet.assess(findings, baseline, touchedPaths = emptySet())

        assertFalse(result.hasBlockingFindings)
        assertEquals(listOf(RatchetStatus.TOLERATED, RatchetStatus.REDUCED), result.assessments.map { it.status })
    }

    @Test
    fun `new and worsened debt is rejected with ratchet rule`() {
        val baseline = baseline(entry("known", 200))
        val findings = listOf(finding("known", 201), finding("new", 1))

        val result = QualityRatchet.assess(findings, baseline, touchedPaths = emptySet())

        assertTrue(result.hasBlockingFindings)
        assertTrue(result.assessments.all { it.status == RatchetStatus.BLOCKING })
        assertTrue(result.assessments.all { it.ratchetRuleId == "LB-RATCHET-001" })
    }

    @Test
    fun `touching a violating file requires a strict reduction`() {
        val baseline = baseline(entry("known", 200))
        val finding = finding("known", 200, setOf("src/Dirty.kt", "src/Partner.kt"))

        val result = QualityRatchet.assess(listOf(finding), baseline, touchedPaths = setOf("src/Partner.kt"))

        assertTrue(result.hasBlockingFindings)
        assertTrue("touched" in result.assessments.single().reason)
    }

    @Test
    fun `aggregated cycle finding reuses monotone legacy edge ceilings`() {
        val sourcePackage = "net.example.features.route"
        val session = "net.example.features.session"
        val runtime = "net.example.features.runtime"
        val baseline = baseline(
            cycleEdge(sourcePackage, session, 2),
            cycleEdge(sourcePackage, runtime, 3),
        )
        val unchanged = cycleFinding(sourcePackage, setOf(session, runtime))
        val reduced = cycleFinding(sourcePackage, setOf(session))

        val toleratedResult = QualityRatchet.assess(listOf(unchanged), baseline, touchedPaths = emptySet())
        val reducedResult = QualityRatchet.assess(listOf(reduced), baseline, touchedPaths = setOf("src/Route.kt"))

        assertEquals(RatchetStatus.TOLERATED, toleratedResult.assessments.single().status)
        assertEquals(RatchetStatus.REDUCED, reducedResult.assessments.single().status)
    }

    @Test
    fun `forbidden dependency legacy occurrence ceiling normalizes to one edge`() {
        val fingerprint = "LB-ARCH-001|net.example.utils|net.example.features.Runtime"
        val baseline = baseline(
            RatchetEntry(fingerprint, "LB-ARCH-001", "src/First.kt", "forbidden-edge", 5),
        )
        val finding = Finding(
            ruleId = "LB-ARCH-001",
            path = "src/First.kt",
            line = 1,
            subject = "net.example.utils->net.example.features.Runtime",
            message = "forbidden dependency",
            recommendation = "invert edge",
            documentation = "docs",
            measuredValue = 1,
            fingerprint = fingerprint,
        )

        val result = QualityRatchet.assess(listOf(finding), baseline, touchedPaths = emptySet())

        assertEquals(RatchetStatus.TOLERATED, result.assessments.single().status)
    }

    @Test
    fun `direct child consumes matching parent forbidden edge without growing source family union`() {
        val parent = "net.example.features.route"
        val child = "$parent.runtime"
        val target = "net.example.integration.Transport"
        val baseline = baseline(architectureEdge(parent, target))
        val moved = architectureFinding(child, target)

        val result = QualityRatchet.assess(listOf(moved), baseline, touchedPaths = moved.relatedPaths)

        assertFalse(result.hasBlockingFindings)
        assertEquals(RatchetStatus.TOLERATED, result.assessments.single().status)
        assertTrue("direct parent" in result.assessments.single().reason)
    }

    @Test
    fun `unmatched target and grandparent edge cannot fund child extraction`() {
        val child = "net.example.features.route.runtime"
        val target = "net.example.integration.Transport"
        val wrongTarget = baseline(architectureEdge("net.example.features.route", "net.example.integration.Other"))
        val grandparent = baseline(architectureEdge("net.example.features", target))
        val finding = architectureFinding(child, target)

        val wrongTargetResult = QualityRatchet.assess(listOf(finding), wrongTarget, finding.relatedPaths)
        val grandparentResult = QualityRatchet.assess(listOf(finding), grandparent, finding.relatedPaths)

        assertTrue(wrongTargetResult.hasBlockingFindings)
        assertTrue(grandparentResult.hasBlockingFindings)
    }

    @Test
    fun `cycle extraction preserves matching parent target union and rejects a new target`() {
        val parent = "net.example.features.route"
        val child = "$parent.runtime"
        val firstTarget = "net.example.features.session"
        val secondTarget = "net.example.features.policy"
        val baseline = baseline(cycleEdge(parent, firstTarget, 4), cycleEdge(parent, secondTarget, 2))
        val moved = cycleFinding(child, setOf(firstTarget, secondTarget))
        val grown = cycleFinding(child, setOf(firstTarget, secondTarget, "net.example.features.newtarget"))

        val movedResult = QualityRatchet.assess(listOf(moved), baseline, moved.relatedPaths)
        val grownResult = QualityRatchet.assess(listOf(grown), baseline, grown.relatedPaths)

        assertFalse(movedResult.hasBlockingFindings)
        assertEquals(RatchetStatus.TOLERATED, movedResult.assessments.single().status)
        assertTrue(grownResult.hasBlockingFindings)
    }

    @Test
    fun `ratchet baseline may remove or lower entries but never add or raise them`() {
        val reference = baseline(entry("kept", 10), entry("removed", 5))
        val valid = baseline(entry("kept", 9))
        val invalid = baseline(entry("kept", 11), entry("added", 1))

        assertTrue(QualityRatchet.baselineIncreases(valid, reference, "config/ratchet.json").isEmpty())
        val increases = QualityRatchet.baselineIncreases(invalid, reference, "config/ratchet.json")

        assertEquals(2, increases.size)
        assertTrue(increases.all { it.ruleId == "LB-RATCHET-002" })
    }

    @Test
    fun `ratchet baseline target sets may shrink but never grow`() {
        val reference = baseline(targetEntry(setOf("net.example.First", "net.example.Second")))
        val reduced = baseline(targetEntry(setOf("net.example.First")))
        val increased = baseline(targetEntry(setOf("net.example.First", "net.example.Second", "net.example.Third")))

        assertTrue(QualityRatchet.baselineIncreases(reduced, reference, "config/ratchet.json").isEmpty())
        val findings = QualityRatchet.baselineIncreases(increased, reference, "config/ratchet.json")

        assertEquals(1, findings.size)
        assertTrue("net.example.Third" in findings.single().message)
    }

    @Test
    fun `ratchet entry identity cannot be rewritten behind a stable fingerprint`() {
        val original = entry("stable", 10)
        val rewritten = original.copy(
            ruleId = "LB-ARCH-001",
            path = "src/Rewritten.kt",
            subject = "rewritten-edge",
        )

        val findings = QualityRatchet.baselineIncreases(
            baseline(rewritten),
            baseline(original),
            "config/ratchet.json",
        )

        assertEquals(1, findings.size)
        assertTrue("rewrites identity" in findings.single().message)
    }

    @Test
    fun `ratchet rejects duplicate fingerprints and negative ceilings`() {
        assertFailsWith<IllegalArgumentException> { baseline(entry("duplicate", 1), entry("duplicate", 1)) }
        assertFailsWith<IllegalArgumentException> { baseline(entry("negative", -1)) }
    }

    private fun finding(fingerprint: String, measured: Int, paths: Set<String> = setOf("src/Dirty.kt")) = Finding(
        ruleId = "LB-HYG-001",
        path = paths.first(),
        line = 1,
        subject = fingerprint,
        message = "dirty",
        recommendation = "extract",
        documentation = "docs",
        measuredValue = measured,
        fingerprint = fingerprint,
        relatedPaths = paths,
    )

    private fun entry(fingerprint: String, maximum: Int) = RatchetEntry(
        fingerprint = fingerprint,
        ruleId = "LB-HYG-001",
        path = "src/Dirty.kt",
        subject = fingerprint,
        maximum = maximum,
    )

    private fun cycleEdge(source: String, target: String, maximum: Int) = RatchetEntry(
        fingerprint = "LB-ARCH-002|$source|$target",
        ruleId = "LB-ARCH-002",
        path = "src/Route.kt",
        subject = "$source->$target",
        maximum = maximum,
    )

    private fun targetEntry(targets: Set<String>) = RatchetEntry(
        fingerprint = "LB-ARCH-002|net.example.source",
        ruleId = "LB-ARCH-002",
        path = "src/Source.kt",
        subject = "cycle:net.example.source",
        maximum = 2,
        targets = targets,
    )

    private fun architectureEdge(source: String, target: String) = RatchetEntry(
        fingerprint = "LB-ARCH-001|$source|$target",
        ruleId = "LB-ARCH-001",
        path = "src/Parent.kt",
        subject = "$source->$target",
        maximum = 1,
    )

    private fun architectureFinding(source: String, target: String) = Finding(
        ruleId = "LB-ARCH-001",
        path = "src/Child.kt",
        line = 1,
        subject = "$source->$target",
        message = "forbidden dependency",
        recommendation = "invert edge",
        documentation = "docs",
        measuredValue = 1,
        fingerprint = "LB-ARCH-001|$source|$target",
        ratchetAliases = setOf(source),
        ratchetTargets = setOf(target),
    )

    private fun cycleFinding(source: String, targets: Set<String>) = Finding(
        ruleId = "LB-ARCH-002",
        path = "src/Child.kt",
        line = 1,
        subject = "cycle:$source",
        message = "cyclic package",
        recommendation = "invert edge",
        documentation = "docs",
        measuredValue = targets.size,
        fingerprint = "LB-ARCH-002|$source",
        ratchetAliases = setOf(source),
        ratchetTargets = targets,
    )

    private fun baseline(vararg entries: RatchetEntry) = RatchetBaseline(1, "abc123", entries.toList())
}
