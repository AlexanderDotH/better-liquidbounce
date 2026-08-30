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
package net.ccbluex.liquidbounce.features.module.modules.world.liquidfiller

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleLiquidFillerContractTest {

    @Test
    fun `settings retain names defaults and registration order`() {
        assertInOrder(
            moduleSource,
            "multiEnumChoice(\"PlaceIn\", enumSetAllOf<PlaceIn>(), canBeNone = false)",
            "enumChoice(\"PlaceOrder\", PlaceOrder.FURTHER_FIRST)",
            "boolean(\"UseSponge\", false)",
            "enumChoice(\"Filter\", Filter.WHITELIST)",
            "blocks(\n        \"Blocks\"",
            "tree(BlockPlacer(",
            "Priority.NORMAL",
            "::findSlotForTarget",
            "allowSupportPlacements = false",
        )
        assertTrue("private enum class PlaceIn" in moduleSource)
        assertTrue("private enum class PlaceOrder" in moduleSource)
    }

    @Test
    fun `availability scan ordering and sponge projection remain stable`() {
        assertInOrder(
            moduleSource.substringAfter("private val targetUpdater"),
            "findSlotForTarget(null) == null",
            "if (!placer.isDone())",
            "placer.clear()",
            "return@handler",
            "placer.update(findFillTargets())",
        )
        assertInOrder(
            moduleSource.substringAfter("private fun findFillTargets()"),
            "max(placer.range, placer.wallRange).toDouble()",
            "filter.getSlot(blocks)",
            "spongeSlot()",
            "eyePos.searchBlocksInCuboid",
            "shouldFill(state, normalFillSlot != null, spongeSlot != null)",
            "placeOrder.sort(positions, eyePos)",
            "positions.mapNotNull",
            "useSponge && isWaterTarget(target)",
            "findSpongePlacement(target, scanRange)",
        )
    }

    @Test
    fun `fluid slot and placement decisions keep their exact precedence`() {
        assertInOrder(
            moduleSource.substringAfter("private fun shouldFill"),
            "if (!state.canBeReplaced())",
            "fluidState.isEmpty || !fluidState.isSource",
            "PlaceIn.WATER in placeIn",
            "if (useSponge) hasSponge else hasNormalFiller",
            "PlaceIn.LAVA in placeIn",
        )
        assertInOrder(
            moduleSource.substringAfter("private fun findSlotForTarget"),
            "val spongeSlot = spongeSlot()",
            "val normalFillSlot = filter.getSlot(blocks)",
            "return spongeSlot ?: normalFillSlot",
            "useSponge && isWaterTarget(pos) -> spongeSlot",
            "else -> normalFillSlot",
        )
        assertInOrder(
            moduleSource.substringAfter("private fun findSpongePlacement"),
            "pos.distToCenterSqr(player.eyePosition) <= scanRange.sq()",
            "state.canBeReplaced()",
            "pos.hasAnySolidPlacementNeighbor()",
            "!pos.isBlockedByEntities()",
            "spongeWaterReachability.canAbsorbFrom(pos, waterPos)",
            "minByOrNull { (pos, _) -> pos.distToCenterSqr(player.eyePosition) }",
        )
    }

    @Test
    fun `module delegates sponge reachability without structural suppression`() {
        assertTrue("SpongeWaterReachability { pos -> pos.state }" in moduleSource)
        assertTrue("state.fluidState.`is`(FluidTags.WATER)" in reachabilitySource)
        assertFalse("@Suppress(\"CognitiveComplexMethod\")" in moduleSource)
        assertFalse("BlockPos.breadthFirstTraversal" in moduleSource)
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
        val moduleSource: String = Files.readString(
            Path.of("src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/ModuleLiquidFiller.kt")
        )
        val reachabilitySource: String = Files.readString(
            Path.of(
                "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/liquidfiller/" +
                    "SpongeWaterReachability.kt"
            )
        )
    }
}
