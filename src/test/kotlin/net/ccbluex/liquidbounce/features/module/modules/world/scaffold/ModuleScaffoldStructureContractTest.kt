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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ModuleScaffoldStructureContractTest {

    @Test
    fun `root settings retain their names defaults and registration order`() {
        assertInOrder(
            moduleSource,
            "intRange(\"Delay\", 0..0, 0..40, \"ticks\")",
            "float(\"MinDist\", 0.0f, 0.0f..0.25f)",
            "float(\"Timer\", 1f, 0.01f..10f)",
            "tree(ScaffoldBlockItemSelection)",
            "tree(ScaffoldAutoBlockFeature)",
            "tree(ScaffoldMovementPrediction)",
            "\"Technique\"",
            "enumChoice(\"SameY\", SameYMode.OFF)",
            "choices(\"Tower\", 0)",
            "choices(\"SafeWalk\", 1, ModuleSafeWalk::safeWalkChoices)",
            "enumChoice(\"Swing\", SwingMode.DO_NOT_HIDE)",
            "ToggleableValueGroup(this, \"SimulatePlacementAttempts\", false)",
            "tree(ScaffoldRotationValueGroup)",
            "tree(ScaffoldSprintControlFeature)",
            "tree(SimulatePlacementAttempts)",
            "boolean(\"AutoSpeed\", false)",
            "boolean(\"Ledge\", true)",
            "PlacementRenderer(\"Render\", true, this, keep = false)",
        )
    }

    @Test
    fun `block selection is delegated while the public comparator facade remains`() {
        assertFalse("@Suppress(\"TooManyFunctions\")" in moduleSource)
        assertTrue("val blockCount: Int" in moduleSource)
        assertTrue("get() = ScaffoldBlockItemSelection.blockCount" in moduleSource)
        assertTrue("@JvmField" in moduleSource)
        assertTrue("val BLOCK_COMPARATOR_FOR_INVENTORY" in moduleSource)
        assertTrue("ScaffoldBlockItemSelection.inventoryComparator" in moduleSource)
        assertFalse("private fun findPlaceableSlots" in moduleSource)
        assertFalse("private fun findBestValidHotbarSlotForTarget" in moduleSource)
        assertFalse("private fun handleSilentBlockSelection" in moduleSource)
    }

    @Test
    fun `block ranking and fallback order stay stable`() {
        assertInOrder(
            selectionSource,
            "private val hotbarComparator",
            "PreferFavourableBlocks",
            "PreferSolidBlocks",
            "PreferFullCubeBlocks",
            "PreferWalkableBlocks",
            "PreferAverageHardBlocks(neutralRange = true)",
            "PreferStackSize.PREFER_MORE",
            "PreferAverageHardBlocks(neutralRange = false)",
            "internal val inventoryComparator",
            "PreferStackSize.PREFER_FEWER",
        )
        assertInOrder(
            selectionSource,
            "stack.count > doNotUseBelowCount",
            "maxWithOrNull { first, second -> hotbarComparator.compare(first.value, second.value) }",
            "placeableSlots.maxWithOrNull",
        )
    }

    @Test
    fun `selection rotation placement and movement timing stay stable`() {
        val tickSource = moduleSource.substringAfter("private val tickHandler = tickHandler")
        assertInOrder(
            tickSource,
            "if (ScaffoldAutoBlockFeature.alwaysHoldBlock)",
            "ScaffoldBlockItemSelection.ensureBlockInMainHand(",
            "val suitableHand",
            "if (simulatePlacementAttempts",
            "if (target == null || currentCrosshairTarget == null)",
            "if (!target.doesCrosshairTargetMatchRequirements",
            "if (!ScaffoldAutoBlockFeature.alwaysHoldBlock)",
            "ScaffoldBlockItemSelection.ensureBlockInMainHand(",
            "if (!hasBlockInMainHand && !hasBlockInOffHand)",
            "if (rotationTiming == ON_TICK || rotationTiming == ON_TICK_SNAP)",
            "if (rotationTiming == ON_TICK_SNAP)",
            "doPlacement(currentCrosshairTarget, currentRotation, handToInteractWith",
            "if (rotationTiming == ON_TICK",
            "ScaffoldMovementPrediction.onPlace",
            "waitTicks(currentDelay)",
        )

        assertInOrder(
            moduleSource,
            "priority = EventPriorityConvention.MODEL_STATE",
            "priority = EventPriorityConvention.SAFETY_FEATURE",
            "if (forceSneak > 0)",
            "event.sneak = true",
            "val ledgeAction = ledge(",
            "if (ledgeAction.jump)",
            "if (ledgeAction.stopInput)",
            "if (ledgeAction.stepBack)",
            "if (ledgeAction.sneakTime > forceSneak)",
        )
        assertInOrder(
            tickSource,
            "ScaffoldEagleFeature.onBlockPlacement()",
            "ScaffoldBlinkFeature.onBlockPlacement()",
            "ScaffoldSprintControlFeature.onBlockPlacement()",
        )
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
        val SCAFFOLD_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold",
        )
        val moduleSource: String = Files.readString(SCAFFOLD_ROOT.resolve("ModuleScaffold.kt"))
        val selectionSource: String = Files.readString(SCAFFOLD_ROOT.resolve("ScaffoldBlockItemSelection.kt"))
    }
}
