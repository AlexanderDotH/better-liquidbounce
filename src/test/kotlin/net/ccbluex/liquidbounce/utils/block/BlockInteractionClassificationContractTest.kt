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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockInteractionClassificationContractTest {

    @Test
    fun `public block interaction extensions retain their JVM facade and signatures`() {
        assertTrue("@file:JvmName(\"BlockExtensionsKt\")" in facadeSource)
        assertTrue("@file:JvmMultifileClass" in facadeSource)
        assertFalse("TooManyFunctions" in facadeSource)
        assertTrue(
            "fun Block?.isInteractable(blockState: BlockState?): Boolean =" in facadeSource,
        )
        assertTrue(
            "BlockInteractionClassifier.isInteractable(this, blockState)" in facadeSource,
        )
        assertTrue(
            "val BlockState?.isInteractable: Boolean get() = this?.block?.isInteractable(this) ?: false" in
                facadeSource,
        )
    }

    @Test
    fun `all historically interactable block families remain classified`() {
        val expectedTypes = listOf(
            "BedBlock", "AbstractChestBlock", "AbstractFurnaceBlock", "AnvilBlock", "BarrelBlock",
            "BeaconBlock", "BellBlock", "BrewingStandBlock", "ButtonBlock", "CakeBlock", "CandleCakeBlock",
            "CartographyTableBlock", "CaveVinesPlantBlock", "CaveVinesBlock", "ComparatorBlock",
            "ComposterBlock", "CrafterBlock", "CraftingTableBlock", "DaylightDetectorBlock",
            "DecoratedPotBlock", "DispenserBlock", "DoorBlock", "DragonEggBlock", "EnchantingTableBlock",
            "FenceGateBlock", "FlowerPotBlock", "GrindstoneBlock", "HopperBlock", "GameMasterBlock",
            "JukeboxBlock", "LecternBlock", "LeverBlock", "LightBlock", "NoteBlock", "RedStoneWireBlock",
            "RepeaterBlock", "RespawnAnchorBlock", "ShulkerBoxBlock", "StonecutterBlock",
            "SweetBerryBushBlock", "TrapDoorBlock",
        )

        expectedTypes.forEach { type ->
            assertTrue(
                Regex("""\bis\s+${Regex.escape(type)}(?:<\*>)?""").containsMatchIn(classifierSource),
                type,
            )
        }
    }

    @Test
    fun `state and player dependent interaction guards retain their fallback values`() {
        assertTrue("this is CakeBlock && player.foodData.needsFood()" in classifierSource)
        assertTrue(
            "this is CaveVinesPlantBlock && (blockState?.getValue(CaveVines.BERRIES) ?: true)" in
                classifierSource,
        )
        assertTrue(
            "this is CaveVinesBlock && (blockState?.getValue(CaveVines.BERRIES) ?: true)" in classifierSource,
        )
        assertTrue(
            "this is ComposterBlock && (blockState?.getValue(ComposterBlock.LEVEL) ?: 8) == 8" in
                classifierSource,
        )
        assertTrue("this is JukeboxBlock && blockState?.getValue(JukeboxBlock.HAS_RECORD) == true" in classifierSource)
        assertTrue(
            "this is SweetBerryBushBlock && (blockState?.getValue(SweetBerryBushBlock.AGE) ?: 2) > 1" in
                classifierSource,
        )
        assertEquals(2, Regex("player\\.canUseGameMasterBlocks\\(\\)").findAll(classifierSource).count())
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/block")
        val facadeSource: String = Files.readString(SOURCE_ROOT.resolve("BlockInteractionClassification.kt"))
        val classifierSource: String = Files.readString(SOURCE_ROOT.resolve("BlockInteractionClassifier.kt"))
    }
}
