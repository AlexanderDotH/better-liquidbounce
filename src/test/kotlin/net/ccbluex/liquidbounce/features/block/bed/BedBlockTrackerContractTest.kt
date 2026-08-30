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
package net.ccbluex.liquidbounce.features.block.bed

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BedBlockTrackerContractTest {

    @Test
    fun `tracker retains its public facade without obsolete structural suppression`() {
        PUBLIC_FACADE_MARKERS.forEach { marker ->
            assertTrue(marker in source, "BedBlockTracker must retain `$marker`")
        }
        assertFalse(
            "@Suppress(\"detekt:CognitiveComplexMethod\")" in source,
            "getStateFor no longer needs a cognitive-complexity suppression",
        )
    }

    @Test
    fun `bed updates select only the first head before creating tracked state`() {
        getStateFor.assertInOrder(
            "if (state.isBed)",
            "val part = BedBlock.getBlockType(state)",
            "if (part == DoubleBlockCombiner.BlockType.FIRST)",
            "pos.getBedPlates(state)",
            "else",
            "null",
            "A non-bed block was updated",
        )
    }

    @Test
    fun `non-bed updates invalidate or refresh nearby tracked beds in order`() {
        getStateFor.assertInOrder(
            "val distance = maxLayers",
            "allPositions().forEach { bedPos ->",
            "if (bedPos.distManhattan(pos) > distance)",
            "return@forEach",
            "val bedState = bedPos.state",
            "if (bedState == null || !bedState.isBed)",
            "untrack(bedPos)",
            "else",
            "track(bedPos, bedPos.getBedPlates(bedState))",
            "null",
        )
    }

    @Test
    fun `surrounding scan reads cached positions before classifying their states`() {
        surroundingScan.assertInOrder(
            "val layers =",
            "val pos = CACHE.get()",
            "for ((layer, longValue) in searchBedLayer(blockState, maxLayers))",
            "val state = pos.set(longValue).state",
            "if (state == null || state.isAir)",
            "continue",
            "layers[layer - 1].addTo(state.block, 1)",
        )
    }

    private fun String.assertInOrder(vararg markers: String) {
        var cursor = 0
        markers.forEach { marker ->
            val index = indexOf(marker, cursor)
            assertTrue(index >= cursor, "Expected `$marker` after offset $cursor")
            cursor = index + marker.length
        }
    }

    private companion object {
        val SOURCE_PATH: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block/bed/BedBlockTracker.kt",
        )
        val source: String = Files.readString(SOURCE_PATH)
        val getStateFor: String = source.substringAfter("override fun getStateFor(")
            .substringBefore("override fun onUpdated()")
        val surroundingScan: String = source.substringAfter("private fun BlockPos.getBedSurroundingBlocks(")
            .substringBefore("private fun BlockPos.getBedPlates(")
        val PUBLIC_FACADE_MARKERS = listOf(
            "object BedBlockTracker : AbstractBlockLocationTracker.BlockPos2State<BedState>()",
            "fun subscribe(subscriber: Subscriber)",
            "fun unsubscribe(subscriber: Subscriber)",
            "override fun getStateFor(pos: BlockPos, state: BlockState): BedState?",
            "override fun onUpdated()",
            "interface Subscriber",
            "val maxLayers: Int",
        )
    }
}
