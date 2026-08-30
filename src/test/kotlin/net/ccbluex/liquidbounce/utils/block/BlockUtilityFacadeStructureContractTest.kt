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

class BlockUtilityFacadeStructureContractTest {

    @Test
    fun `focused block utilities retain their shared JVM facade without structural suppressions`() {
        facadeSources.forEach { (fileName, source) ->
            assertTrue("@file:JvmName(\"BlockExtensionsKt\")" in source, "$fileName must retain the JVM facade")
            assertTrue("@file:JvmMultifileClass" in source, "$fileName must remain part of the multifile facade")
            assertTrue("package $BLOCK_PACKAGE" in source, "$fileName must remain in the block package")
            assertFalse("TooManyFunctions" in source, "$fileName must not suppress structural debt")
        }
    }

    @Test
    fun `focused block utility public surface remains available exactly once`() {
        expectedDeclarations.forEach { (fileName, declarations) ->
            val source = facadeSources.getValue(fileName)
            declarations.forEach { declaration ->
                assertTrue(
                    Regex(declaration).findAll(source).count() == 1,
                    "$fileName must retain exactly one declaration matching $declaration",
                )
            }
        }
    }

    @Test
    fun `state access retains its deprecated JVM name and Java immutable bridge`() {
        val stateAccess = facadeSources.getValue("BlockStateAccess.kt")
        assertInOrder(
            stateAccess,
            "@Deprecated(",
            "@JvmName(\"getState-deprecated\")",
            "inline fun BlockPos.getState() = state",
        )
        assertTrue("val BlockPos.immutable: BlockPos" in stateAccess)
        assertTrue("import net.ccbluex.liquidbounce.utils.block.BlockExtensionsKt;" in mixinLevelSource)
        assertTrue("BlockExtensionsKt.getImmutable(pos)" in mixinLevelSource)
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
        const val BLOCK_PACKAGE = "net.ccbluex.liquidbounce.utils.block"
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/block")
        val facadeSources: Map<String, String> = listOf(
            "BlockProperties.kt",
            "BlockSearch.kt",
            "BlockStateAccess.kt",
        ).associateWith { Files.readString(SOURCE_ROOT.resolve(it)) }
        val mixinLevelSource: String = Files.readString(
            Path.of("src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinLevel.java"),
        )
        val expectedDeclarations: Map<String, List<String>> = mapOf(
            "BlockProperties.kt" to listOf(
                "fun\\s+BlockState\\.isNotBreakable\\s*\\(pos:\\s*BlockPos\\)",
                "fun\\s+BlockState\\.isBreakable\\s*\\(pos:\\s*BlockPos\\):\\s*Boolean",
                "fun\\s+BlockPos\\?\\.fallDamageMultiplier\\s*\\(entity:\\s*Entity\\):\\s*Float",
                "fun\\s+Block\\?\\.fallDamageMultiplier\\s*\\(entity:\\s*Entity\\):\\s*Float",
                "fun\\s+BlockPos\\.isBlastResistant\\s*\\(\\):\\s*Boolean",
                "fun\\s+RespawnAnchorBlock\\.isCharged\\s*\\(state:\\s*BlockState\\):\\s*Boolean",
                "fun\\s+BedBlock\\.getPotentialSecondBedBlock\\s*\\(state:\\s*BlockState,\\s*pos:\\s*BlockPos\\):\\s*BlockPos",
            ),
            "BlockSearch.kt" to listOf(
                "fun\\s+Vec3\\.searchBlocksInCuboid\\s*\\(radius:\\s*Float\\):\\s*Iterable<BlockPos>",
                "fun\\s+Vec3\\.searchBlocksInCuboid\\s*\\(\\s*radius:\\s*Float,\\s*crossinline\\s+filter:",
                "fun\\s+Vec3\\.searchBlocksInRangeSorted\\s*\\(",
                "fun\\s+BlockPos\\.searchBedLayer\\s*\\(state:\\s*BlockState,\\s*layers:\\s*Int\\):\\s*Sequence<IntLongPair>",
                "fun\\s+BlockPos\\.searchLayer\\s*\\(layers:\\s*Int,\\s*vararg\\s+directions:\\s*Direction\\):\\s*Sequence<IntLongPair>",
                "fun\\s+BlockPos\\.getSortedSphere\\s*\\(radius:\\s*Float\\):\\s*Array<BlockPos>",
            ),
            "BlockStateAccess.kt" to listOf(
                "fun\\s+Vec3i\\.toBlockPos\\s*\\(\\)",
                "val\\s+BlockPos\\.state:\\s*BlockState\\?",
                "fun\\s+BlockPos\\.getState\\s*\\(\\)",
                "val\\s+BlockPos\\.stateOrEmpty:\\s*BlockState",
                "fun\\s+BlockPos\\.getBlock\\s*\\(\\):\\s*Block\\?",
                "fun\\s+BlockPos\\.getCenterDistanceSquared\\s*\\(\\)",
                "fun\\s+BlockPos\\.getCenterDistanceSquaredEyes\\s*\\(\\)",
                "val\\s+BlockState\\.isBed:\\s*Boolean",
                "val\\s+TypedInstance<Block>\\.isAnyChest:\\s*Boolean",
                "val\\s+BlockPos\\.immutable:\\s*BlockPos",
                "val\\s+BlockPos\\.outlineBox:\\s*AABB",
                "val\\s+BlockPos\\.collisionShape:\\s*VoxelShape",
                "val\\s+BlockPos\\.outlineShape:\\s*VoxelShape",
                "fun\\s+BlockState\\.outlineBox\\s*\\(blockPos:\\s*BlockPos\\):\\s*AABB",
                "val\\s+Block\\.mustBePlacedOnUpperSide:\\s*Boolean",
            ),
        )
    }
}
