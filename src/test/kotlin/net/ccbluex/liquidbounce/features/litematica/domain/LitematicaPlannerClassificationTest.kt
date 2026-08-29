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

package net.ccbluex.liquidbounce.features.litematica.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LitematicaPlannerClassificationTest {

    private val planner = LitematicaPlanner()

    @Test
    fun `matching block and properties are correct while block and state mismatches stay distinct`() {
        val plan = plan(
            cell(0, desired = stairs("north"), actual = stairs("north")),
            cell(1, desired = STONE, actual = DIRT),
            cell(2, desired = stairs("north"), actual = stairs("south")),
        )

        assertEquals(
            listOf(
                LitematicaCellStatus.CORRECT,
                LitematicaCellStatus.WRONG_BLOCK,
                LitematicaCellStatus.WRONG_STATE,
            ),
            plan.cells.map { it.status },
        )
    }

    @Test
    fun `air and replaceable targets are missing while schematic air makes an extra block`() {
        val plan = plan(
            cell(0, desired = STONE, actual = AIR),
            cell(1, desired = STONE, actual = TALL_GRASS),
            cell(2, desired = AIR, actual = DIRT),
        )

        assertEquals(
            listOf(
                LitematicaCellStatus.MISSING,
                LitematicaCellStatus.MISSING,
                LitematicaCellStatus.EXTRA,
            ),
            plan.cells.map { it.status },
        )
    }

    @Test
    fun `desired source fluid is classified and scheduled after solid placements`() {
        val plan = plan(
            cell(0, desired = WATER, actual = AIR),
            cell(1, desired = STONE, actual = AIR),
        )

        assertEquals(LitematicaCellStatus.SOURCE_FLUID, plan.cells.first().status)
        assertEquals(
            listOf(LitematicaActionKind.PLACE, LitematicaActionKind.FLUID_PLACE),
            plan.actions.map { it.kind },
        )
    }

    @Test
    fun `extra source fluid uses bucket pickup as cleanup`() {
        val plan = plan(cell(0, desired = AIR, actual = WATER))

        assertEquals(LitematicaCellStatus.EXTRA, plan.cells.single().status)
        assertEquals(LitematicaActionKind.FLUID_PICKUP, plan.actions.single().kind)
        assertEquals(LitematicaActionPriority.CLEANUP, plan.actions.single().priority)
    }

    @Test
    fun `existing block entity is protected until risky breaking is explicitly enabled`() {
        val protected = plan(cell(0, desired = AIR, actual = CHEST))
        val enabled = plan(
            cell(0, desired = AIR, actual = CHEST),
            settings = LitematicaPlannerSettings(breakBlockEntities = true),
        )

        assertEquals(LitematicaCellStatus.PROTECTED, protected.cells.single().status)
        assertEquals(LitematicaBlockReason.BLOCK_ENTITY_PROTECTED, protected.cells.single().blockReason)
        assertTrue(protected.actions.isEmpty())
        assertEquals(LitematicaCellStatus.EXTRA, enabled.cells.single().status)
        assertEquals(LitematicaActionKind.BREAK, enabled.actions.single().kind)
    }

    @Test
    fun `pending confirmation overrides a still stale world mismatch`() {
        val target = LitematicaPosition(0, 64, 0)
        val plan = plan(
            cell(0, desired = STONE, actual = AIR),
            pendingPositions = setOf(target),
        )

        assertEquals(LitematicaCellStatus.PENDING, plan.cells.single().status)
        assertEquals(LitematicaBlockReason.PENDING_CONFIRMATION, plan.cells.single().blockReason)
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `flowing fluids and non reproducible desired blocks are unsupported`() {
        val plan = plan(
            cell(0, desired = STONE, actual = FLOWING_WATER),
            cell(1, desired = NON_REPRODUCIBLE_CHEST, actual = AIR),
        )

        assertEquals(
            listOf(LitematicaCellStatus.UNSUPPORTED, LitematicaCellStatus.UNSUPPORTED),
            plan.cells.map { it.status },
        )
        assertEquals(
            listOf(LitematicaBlockReason.FLOWING_FLUID, LitematicaBlockReason.NON_REPRODUCIBLE_BLOCK),
            plan.cells.map { it.blockReason },
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `cleanup supported solids airplace solids and fluids have strict priority`() {
        val plan = plan(
            cell(4, desired = WATER, actual = AIR),
            cell(3, desired = STONE, actual = AIR, placementMethod = LitematicaPlacementMethod.AIR_PLACE),
            cell(2, desired = STONE, actual = AIR),
            cell(1, desired = AIR, actual = DIRT),
        )

        assertEquals(
            listOf(
                LitematicaActionPriority.CLEANUP,
                LitematicaActionPriority.SUPPORTED_SOLID,
                LitematicaActionPriority.AIRPLACE_SOLID,
                LitematicaActionPriority.DESIRED_FLUID,
            ),
            plan.actions.map { it.priority },
        )
        assertEquals(LitematicaPosition(1, 64, 0), plan.target?.target)
    }

    @Test
    fun `actions inside the same priority are nearest first with position tie break`() {
        val plan = plan(
            cell(3, desired = AIR, actual = DIRT),
            cell(-1, desired = AIR, actual = DIRT),
            cell(1, desired = AIR, actual = DIRT),
        )

        assertEquals(
            listOf(
                LitematicaPosition(-1, 64, 0),
                LitematicaPosition(1, 64, 0),
                LitematicaPosition(3, 64, 0),
            ),
            plan.actions.map { it.target },
        )
    }

    @Test
    fun `disabled cleanup settings preserve classification but suppress actions`() {
        val plan = plan(
            cell(0, desired = AIR, actual = DIRT),
            cell(1, desired = STONE, actual = DIRT),
            settings = LitematicaPlannerSettings(breakExtra = false, breakWrong = false),
        )

        assertEquals(
            listOf(LitematicaCellStatus.EXTRA, LitematicaCellStatus.WRONG_BLOCK),
            plan.cells.map { it.status },
        )
        assertEquals(
            listOf(LitematicaBlockReason.BREAK_EXTRA_DISABLED, LitematicaBlockReason.BREAK_WRONG_DISABLED),
            plan.cells.map { it.blockReason },
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `smart airplace can be disabled without altering the existing placement mode`() {
        val plan = plan(
            cell(0, placementMethod = LitematicaPlacementMethod.AIR_PLACE),
            settings = LitematicaPlannerSettings(airPlace = LitematicaAirPlaceMode.DISABLED),
        )

        assertEquals(LitematicaCellStatus.MISSING, plan.cells.single().status)
        assertEquals(LitematicaBlockReason.AIR_PLACE_DISABLED, plan.cells.single().blockReason)
        assertNull(plan.cells.single().action)
    }

    @Test
    fun `unavailable support and missing material remain visible blocked work`() {
        val noFace = plan(cell(0, placementMethod = LitematicaPlacementMethod.UNAVAILABLE))
        val noMaterial = plan(cell(0, materialAvailable = false, requiredMaterialId = "minecraft:stone"))

        assertEquals(LitematicaBlockReason.NO_PLACEMENT_FACE, noFace.cells.single().blockReason)
        assertEquals(LitematicaBlockReason.MISSING_MATERIAL, noMaterial.cells.single().blockReason)
        assertEquals("minecraft:stone", noMaterial.missingMaterials.single())
        assertTrue(noFace.actions.isEmpty())
        assertTrue(noMaterial.actions.isEmpty())
    }

    @Test
    fun `fluid setting blocks source placement and pickup without guessing`() {
        val plan = plan(
            cell(0, desired = WATER, actual = AIR),
            cell(1, desired = AIR, actual = WATER),
            settings = LitematicaPlannerSettings(fluids = false),
        )

        assertEquals(
            listOf(LitematicaBlockReason.FLUIDS_DISABLED, LitematicaBlockReason.FLUIDS_DISABLED),
            plan.cells.map { it.blockReason },
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `planner settings expose the module defaults and reject invalid limits`() {
        val defaults = LitematicaPlannerSettings()

        assertEquals(LitematicaActivationMode.LITEMATICA_KEY, defaults.activation)
        assertEquals(4.5, defaults.range)
        assertEquals(1, defaults.actionDelayTicks)
        assertEquals(10, defaults.retryLimit)
        assertEquals(LitematicaAirPlaceMode.SMART, defaults.airPlace)
        assertTrue(defaults.breakWrong)
        assertTrue(defaults.breakExtra)
        assertEquals(false, defaults.breakBlockEntities)
        assertTrue(defaults.fluids)
        assertFailsWith<IllegalArgumentException> { LitematicaPlannerSettings(range = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { LitematicaPlannerSettings(actionDelayTicks = -1) }
        assertFailsWith<IllegalArgumentException> { LitematicaPlannerSettings(retryLimit = 0) }
    }

    private fun plan(
        vararg cells: LitematicaCellSnapshot,
        settings: LitematicaPlannerSettings = LitematicaPlannerSettings(),
        pendingPositions: Set<LitematicaPosition> = emptySet(),
    ): LitematicaPrintPlan {
        val positions = cells.map(LitematicaCellSnapshot::position)
        val placement = LitematicaPlacementSnapshot(
            id = LitematicaPlacementId("test"),
            name = "Test",
            enabled = true,
            rendered = true,
            bounds = LitematicaBounds.enclosing(positions),
            cells = cells.asList(),
        )
        return planner.plan(
            LitematicaPlanRequest(
                origin = LitematicaPoint(0.5, 64.5, 0.5),
                placements = listOf(placement),
                pendingPositions = pendingPositions,
                settings = settings,
            ),
        )
    }

    private fun cell(
        x: Int,
        desired: LitematicaBlockSnapshot = STONE,
        actual: LitematicaBlockSnapshot = AIR,
        placementMethod: LitematicaPlacementMethod = LitematicaPlacementMethod.NEIGHBOR_FACE,
        materialAvailable: Boolean = true,
        requiredMaterialId: String? = desired.id.takeUnless { desired.kind == LitematicaBlockKind.AIR },
    ) = LitematicaCellSnapshot(
        position = LitematicaPosition(x, 64, 0),
        desired = desired,
        actual = actual,
        placementMethod = placementMethod,
        materialAvailable = materialAvailable,
        requiredMaterialId = requiredMaterialId,
    )

    private fun stairs(facing: String) = LitematicaBlockSnapshot.solid(
        id = "minecraft:oak_stairs",
        properties = mapOf("facing" to facing, "half" to "bottom"),
    )

    private companion object {
        val AIR = LitematicaBlockSnapshot.air()
        val STONE = LitematicaBlockSnapshot.solid("minecraft:stone")
        val DIRT = LitematicaBlockSnapshot.solid("minecraft:dirt")
        val TALL_GRASS = LitematicaBlockSnapshot.solid("minecraft:tall_grass", replaceable = true)
        val CHEST = LitematicaBlockSnapshot.solid("minecraft:chest", hasBlockEntity = true)
        val NON_REPRODUCIBLE_CHEST = LitematicaBlockSnapshot.solid(
            "minecraft:chest",
            hasBlockEntity = true,
            reproducible = false,
        )
        val WATER = LitematicaBlockSnapshot.sourceFluid("minecraft:water")
        val FLOWING_WATER = LitematicaBlockSnapshot.flowingFluid("minecraft:water")
    }
}
