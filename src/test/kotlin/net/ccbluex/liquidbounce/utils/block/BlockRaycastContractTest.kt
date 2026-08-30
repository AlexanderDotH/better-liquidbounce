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
package net.ccbluex.liquidbounce.utils.block

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockRaycastContractTest {

    @Test
    fun `custom raycast retains its public JVM facade and traversal order`() {
        assertTrue("@file:JvmName(\"BlockExtensionsKt\")" in facadeSource)
        assertTrue("@file:JvmMultifileClass" in facadeSource)
        assertFalse("TooManyFunctions" in facadeSource)
        assertInOrder(
            functionBody(facadeSource, "raycast"),
            "BlockRaycastTraversal(this, exclude, include, maxBlastResistance)",
            "BlockGetter.traverseBlocks(",
            "context.from",
            "context.to",
            "context",
            "traversal::hit",
            "traversal::miss",
        )
    }

    @Test
    fun `cell traversal resolves block before fluid and chooses the nearest hit`() {
        assertInOrder(
            functionBody(traversalSource, "hit"),
            "filter.isExcluded(pos)",
            "resolveBlockState(pos, excluded)",
            "resolveFluidState(pos, excluded)",
            "context.getBlockShape(blockState, world, pos)",
            "world.clipWithInteractionOverride(",
            "context.getFluidShape(fluidState, world, pos)",
            "fluidShape.clip(from, to, pos)",
            "nearestHit(context, blockHitResult, fluidHitResult)",
        )
        assertInOrder(
            functionBody(traversalSource, "nearestHit"),
            "context.from.distanceToSqr(blockHitResult.location)",
            "context.from.distanceToSqr(fluidHitResult.location)",
            "blockHitDistance <= fluidHitDistance",
            "blockHitResult",
            "fluidHitResult",
        )
    }

    @Test
    fun `excluded included and resistance filtering precedence stays stable`() {
        assertInOrder(
            functionBody(traversalSource, "resolveBlockState"),
            "if (excluded)",
            "Blocks.VOID_AIR.defaultBlockState()",
            "if (filter.isIncluded(pos))",
            "Blocks.OBSIDIAN.defaultBlockState()",
            "world.getBlockState(pos)",
            "filter.allows(blockState)",
            "Blocks.VOID_AIR.defaultBlockState()",
        )
        assertInOrder(
            functionBody(traversalSource, "resolveFluidState"),
            "if (excluded)",
            "Fluids.EMPTY.defaultFluidState()",
            "world.getFluidState(pos)",
            "filter.allows(fluidState)",
            "Fluids.EMPTY.defaultFluidState()",
        )
    }

    @Test
    fun `miss result retains vanilla direction and endpoint calculation`() {
        assertInOrder(
            functionBody(traversalSource, "miss"),
            "context.from.subtract(context.to)",
            "BlockHitResult.miss(",
            "context.to",
            "Direction.getApproximateNearest(direction.x, direction.y, direction.z)",
            "BlockPos.containing(context.to)",
        )
    }

    private fun functionBody(source: String, functionName: String): String {
        val extensionReceiver = "(?:[A-Za-z0-9_?.<>]+\\.)?"
        val signature = Regex("""fun\s+$extensionReceiver${Regex.escape(functionName)}\(""")
            .find(source)?.range?.first
            ?: error("Missing function $functionName")
        val openingBrace = source.indexOf('{', signature)
        require(openingBrace >= 0) { "Missing body for $functionName" }
        var depth = 0
        source.forEachIndexed { index, character ->
            if (index < openingBrace) return@forEachIndexed
            when (character) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(openingBrace, index + 1)
            }
        }
        error("Unclosed body for $functionName")
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
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/block")
        val facadeSource: String = Files.readString(SOURCE_ROOT.resolve("BlockRaycast.kt"))
        val traversalSource: String = Files.readString(SOURCE_ROOT.resolve("BlockRaycastTraversal.kt"))
    }
}
