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
package net.ccbluex.liquidbounce.features.module.modules.world.autobuild

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PortalModeStructureContractTest {

    @Test
    fun `enabling selects a portal before frame and support placement`() {
        assertInOrder(
            enabledSource,
            "phase = Phase.BUILD",
            "portal = PortalCandidateSelector.findBest(BlockPos.containing(player.position()))",
            "if (portal == null)",
            "chat(markAsError(ModuleAutoBuild.message(\"noPosition\")), ModuleAutoBuild)",
            "ModuleAutoBuild.enabled = false",
            "placer.update(portal!!.frameBlocks.filter { it.stateOrEmpty.block !== Blocks.OBSIDIAN })",
            "placer.support.blockedPositions.addAll(portal!!.enclosedBlocks)",
        )
    }

    @Test
    fun `rotation updates finish frame corrections before ignition and shutdown`() {
        assertInOrder(
            rotationSource,
            "if (!placer.isDone())",
            "return@handler",
            "if (phase == Phase.BUILD)",
            "val blocks = portal!!.confirmPlacements()",
            "if (blocks.isNotEmpty())",
            "placer.update(blocks)",
            "return@handler",
            "phase = Phase.IGNITE",
            "placer.addToQueue(portal!!.ignitePos)",
            "else if (phase == Phase.IGNITE)",
            "ModuleAutoBuild.enabled = false",
        )
    }

    @Test
    fun `slot lookup preserves offhand hotbar phase items and failure shutdown`() {
        assertInOrder(
            slotSource,
            "for (it in Slots.OffhandWithHotbar)",
            "val item = it.itemStack.item",
            "if (phase == Phase.IGNITE)",
            "if (item == Items.FLINT_AND_STEEL)",
            "return it",
            "continue",
            "if (item !is BlockItem)",
            "continue",
            "if (item.block == Blocks.OBSIDIAN)",
            "return it",
            "if (phase == Phase.IGNITE)",
            "chat(markAsError(ModuleAutoBuild.message(\"noFlintAndSteel\")), ModuleAutoBuild)",
            "chat(markAsError(ModuleAutoBuild.message(\"noObsidian\")), ModuleAutoBuild)",
            "ModuleAutoBuild.enabled = false",
            "return null",
        )
    }

    @Test
    fun `disabling clears placement exclusions before releasing the portal`() {
        assertInOrder(
            disabledSource,
            "placer.support.blockedPositions.clear()",
            "portal = null",
        )
    }

    @Test
    fun `portal search is delegated without structural suppressions`() {
        assertTrue("PortalCandidateSelector.findBest" in modeSource)
        assertFalse("private fun getPortal" in modeSource)
        assertFalse("CognitiveComplexMethod" in modeSource)
        assertFalse("NestedBlockDepth" in modeSource)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, startIndex = previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val MODE_PATH: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/autobuild/PortalMode.kt",
        )
        val modeSource: String = Files.readString(MODE_PATH)
        val enabledSource: String = modeSource.substringAfter("override fun enabled()")
            .substringBefore("private val targetUpdater")
        val rotationSource: String = modeSource.substringAfter("private val targetUpdater")
            .substringBefore("override fun disabled()")
        val disabledSource: String = modeSource.substringAfter("override fun disabled()")
            .substringBefore("override fun getSlot()")
        val slotSource: String = modeSource.substringAfter("override fun getSlot()")
            .substringBefore("enum class Phase")
    }
}
