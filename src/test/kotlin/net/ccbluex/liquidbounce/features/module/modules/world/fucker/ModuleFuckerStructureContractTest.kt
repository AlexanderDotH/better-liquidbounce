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
package net.ccbluex.liquidbounce.features.module.modules.world.fucker

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class ModuleFuckerStructureContractTest {

    @Test
    fun `settings retain names defaults and registration order`() {
        assertInOrder(
            moduleSource,
            "float(\"Range\", 5F, 1F..6F)",
            "float(\"WallRange\", 0f, 0F..6F)",
            "ToggleableValueGroup(this, \"Entrance\", false)",
            "boolean(\"BreakFree\", true)",
            "tree(FuckerEntrance)",
            "boolean(\"Surroundings\", true)",
            "blocks(\"Targets\", findBlocksEndingWith(\"_BED\", \"DRAGON_EGG\"))",
            "int(\"Delay\", 0, 0..20, \"ticks\")",
            "enumChoice(\"Action\", DestroyAction.DESTROY)",
            "boolean(\"ForceImmediateBreak\", false)",
            "boolean(\"IgnoreOpenInventory\", true)",
            "boolean(\"IgnoreUsingItem\", true)",
            "boolean(\"PrioritizeOverKillAura\", false)",
            "boolean(\"ChestAsFullBlock\", false)",
            "choices(\"SelfBed\", 0, ::isSelfBedChoices)",
            "tree(RotationsValueGroup(this))",
            "PlacementRenderer(\"TargetRendering\", true, this",
        )
    }

    @Test
    fun `direct targets remain ahead of entrance and surrounding targets`() {
        val updateSource = moduleSource.substringAfter("private fun updateCurrentTarget()")
            .substringBefore("private fun clearCurrentTarget()")

        assertInOrder(
            updateSource,
            "validateCurrentTarget(possibleBlocks)",
            "selectDirectTarget(possibleBlocks, effectiveRange)",
            "currentTarget != null",
            "possibleBlocks.forEach { pos -> considerIndirectTarget(pos, effectiveRange) }",
        )
        assertTrue(
            "if (FuckerEntrance.enabled && pos.hasEntrance) effectiveRange else wallRange.toDouble()" in moduleSource
        )
        assertInOrder(
            moduleSource.substringAfter("private fun considerIndirectTarget"),
            "FuckerEntrance.enabled && FuckerEntrance.breakFree",
            "pos.weakestNeighbor ?: return",
            "DestroyAction.DESTROY",
            "if (surroundings)",
            "updateSurroundings(pos)",
        )
    }

    @Test
    fun `packet mine use and destroy execution order remains stable`() {
        val breakerSource = moduleSource.substringAfter("private val breaker = tickHandler")
            .substringBefore("private val cancelBlockBreakingHandler")

        assertInOrder(
            breakerSource,
            "ModuleBlink.running",
            "targetRenderer.addBlock(destroyerTarget.pos)",
            "ModulePacketMine.running && destroyerTarget.action == DestroyAction.DESTROY",
            "ModulePacketMine.setTarget(destroyerTarget.pos)",
            "raytraceBlock(",
            "destroyerTarget.action == DestroyAction.USE",
            "interaction.useItemOn",
            "waitTicks(delay)",
            "doBreak(rayTraceResult, immediate = forceImmediateBreak)",
        )
    }

    @Test
    fun `surrounding trace constants and sampling remain stable`() {
        assertTrue("RAYCAST_TARGET_EPSILON = 0.005" in moduleSource)
        assertTrue("doubleArrayOf(0.1, 0.3, 0.5, 0.7, 0.9)" in finderSource)
        assertTrue("MAX_SURROUNDING_PATH_BLOCKS = 8" in finderSource)
        assertTrue("targetShape.move(target).forAllFaces" in finderSource)
        assertTrue("face.samplePointOnSide(side, a, b)" in finderSource)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/fucker"
        )
        val moduleSource: String = Files.readString(SOURCE_ROOT.resolve("ModuleFucker.kt"))
        val finderSource: String = Files.readString(SOURCE_ROOT.resolve("SurroundingPathFinder.kt"))
    }
}
