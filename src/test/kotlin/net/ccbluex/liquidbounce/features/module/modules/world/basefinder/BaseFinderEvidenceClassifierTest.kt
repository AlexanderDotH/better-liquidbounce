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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaseFinderEvidenceClassifierTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `storage blocks use the documented weights`() {
        assertEquals(4, BaseFinderEvidenceClassifier.storageWeight(Blocks.SHULKER_BOX.defaultBlockState()))
        assertEquals(4, BaseFinderEvidenceClassifier.storageWeight(Blocks.ENDER_CHEST.defaultBlockState()))
        assertEquals(3, BaseFinderEvidenceClassifier.storageWeight(Blocks.CHEST.defaultBlockState()))
        assertEquals(3, BaseFinderEvidenceClassifier.storageWeight(Blocks.HOPPER.defaultBlockState()))
        assertEquals(1, BaseFinderEvidenceClassifier.storageWeight(Blocks.FURNACE.defaultBlockState()))
        assertEquals(0, BaseFinderEvidenceClassifier.storageWeight(Blocks.STONE.defaultBlockState()))
    }

    @Test
    fun `physical player storage anchors exclude utility and entity containers`() {
        listOf(
            "chest",
            "trapped_chest",
            "barrel",
            "copper_chest",
            "ender_chest",
            "shulker_box",
            "red_shulker_box",
        ).forEach { path ->
            assertTrue(BaseFinderEvidenceClassifier.isPhysicalPlayerStorageAnchor(storageAnchor(path)), path)
        }

        listOf("hopper", "furnace", "container_vehicle").forEach { path ->
            assertFalse(BaseFinderEvidenceClassifier.isPhysicalPlayerStorageAnchor(storageAnchor(path)), path)
        }
        assertFalse(
            BaseFinderEvidenceClassifier.isPhysicalPlayerStorageAnchor(
                EvidenceAnchor(BaseCoordinate(0, 64, 0), 3, "entity.container_vehicle"),
            ),
        )
    }

    @Test
    fun `crafted utilities collapse variants into stable categories`() {
        assertEquals("anvil", BaseFinderEvidenceClassifier.utilityCategory(Blocks.ANVIL.defaultBlockState()))
        assertEquals("anvil", BaseFinderEvidenceClassifier.utilityCategory(Blocks.CHIPPED_ANVIL.defaultBlockState()))
        assertEquals("bed", BaseFinderEvidenceClassifier.utilityCategory(Blocks.BED.red().defaultBlockState()))
        assertEquals("smelting", BaseFinderEvidenceClassifier.utilityCategory(Blocks.FURNACE.defaultBlockState()))
        assertEquals("smelting", BaseFinderEvidenceClassifier.utilityCategory(Blocks.SMOKER.defaultBlockState()))
        assertEquals("sign", BaseFinderEvidenceClassifier.utilityCategory(Blocks.OAK_SIGN.defaultBlockState()))
        assertNull(BaseFinderEvidenceClassifier.utilityCategory(Blocks.STONE.defaultBlockState()))
    }

    @Test
    fun `automation classification ignores natural terrain`() {
        assertEquals("piston", BaseFinderEvidenceClassifier.automationCategory(Blocks.PISTON.defaultBlockState()))
        assertEquals("observer", BaseFinderEvidenceClassifier.automationCategory(Blocks.OBSERVER.defaultBlockState()))
        assertEquals("rail", BaseFinderEvidenceClassifier.automationCategory(Blocks.RAIL.defaultBlockState()))
        assertEquals("crop", BaseFinderEvidenceClassifier.automationCategory(Blocks.WHEAT.defaultBlockState()))
        assertNull(BaseFinderEvidenceClassifier.automationCategory(Blocks.STONE.defaultBlockState()))
    }

    @Test
    fun `structural classification recognizes portal and infrastructure evidence`() {
        assertEquals(
            "portal",
            BaseFinderEvidenceClassifier.structuralCategory(Blocks.NETHER_PORTAL.defaultBlockState()),
        )
        assertEquals("bed", BaseFinderEvidenceClassifier.structuralCategory(Blocks.BED.white().defaultBlockState()))
        assertEquals(
            "infrastructure",
            BaseFinderEvidenceClassifier.structuralCategory(Blocks.BEACON.defaultBlockState()),
        )
        assertEquals(
            "decoration",
            BaseFinderEvidenceClassifier.structuralCategory(Blocks.OAK_HANGING_SIGN.defaultBlockState()),
        )
    }

    @Test
    fun `activity sounds are support categories rather than arbitrary noise`() {
        assertEquals("piston", BaseFinderEvidenceClassifier.activityCategory("block.piston.extend"))
        assertEquals("note", BaseFinderEvidenceClassifier.activityCategory("block.note_block.harp"))
        assertEquals("anvil", BaseFinderEvidenceClassifier.activityCategory("block.anvil.use"))
        assertEquals("portal", BaseFinderEvidenceClassifier.activityCategory("block.portal.ambient"))
        assertNull(BaseFinderEvidenceClassifier.activityCategory("ambient.cave"))
    }

    private fun storageAnchor(path: String) =
        EvidenceAnchor(BaseCoordinate(0, 64, 0), 3, "storage.$path")
}
